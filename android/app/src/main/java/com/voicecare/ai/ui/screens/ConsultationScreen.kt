package com.voicecare.ai.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.voicecare.ai.ai.ClinicalDrugLexicon
import com.voicecare.ai.ai.GemmaLlmEngine
import com.voicecare.ai.ai.WhisperAsrEngine
import com.voicecare.ai.data.local.entities.PatientEntity
import com.voicecare.ai.data.repository.VoiceCareRepository
import com.voicecare.ai.ui.theme.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsultationScreen(
    patientId: String?,
    repository: VoiceCareRepository,
    onBackClick: () -> Unit,
    onSaveSuccess: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val whisperEngine = remember { WhisperAsrEngine(context) }
    val gemmaEngine = remember { GemmaLlmEngine(context) }

    val isRecording by whisperEngine.isRecording.collectAsState()
    val liveTranscript by whisperEngine.liveTranscript.collectAsState()
    val audioAmplitude by whisperEngine.audioAmplitude.collectAsState()
    val detectedLanguage by whisperEngine.detectedLanguage.collectAsState()

    // Doctor AI Assist Toggle: Defaults strictly to FALSE (opt-in)
    var assistEnabled by remember { mutableStateOf(false) }
    var isProcessingExtraction by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    // Extracted Data State
    var extractedJsonStr by remember { mutableStateOf("") }
    var extractedData by remember { mutableStateOf<JSONObject?>(null) }
    var editableTranscript by remember { mutableStateOf("") }
    var showManualInput by remember { mutableStateOf(false) }

    // Offline Clinical Checkpoint Dialog States
    var patientEntity by remember { mutableStateOf<PatientEntity?>(null) }
    var showAddVitalsDialog by remember { mutableStateOf(false) }
    var showAddDiagnosisDialog by remember { mutableStateOf(false) }
    var showAddMedicineDialog by remember { mutableStateOf(false) }
    var showAllergyConfirmDialog by remember { mutableStateOf(false) }
    var allergyOverrideConfirmed by remember { mutableStateOf(false) }
    var conflictingAllergyName by remember { mutableStateOf("") }
    var conflictingDrugName by remember { mutableStateOf("") }

    // Load Patient details offline from Room DB
    LaunchedEffect(patientId) {
        if (!patientId.isNullOrBlank()) {
            patientEntity = repository.getPatientById(patientId)
        }
    }

    // Microphone Permission Launcher
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            whisperEngine.startListening()
        } else {
            errorMessage = "Microphone permission is required for on-device voice recording."
        }
    }

    // Keep editable transcript synced when recording updates
    LaunchedEffect(liveTranscript) {
        if (liveTranscript.isNotBlank()) {
            editableTranscript = liveTranscript
        }
    }

    // Clean up microphone & ASR engine when leaving consultation screen
    DisposableEffect(Unit) {
        onDispose {
            if (whisperEngine.isRecording.value) {
                whisperEngine.stopListening()
            }
        }
    }

    // Offline Drug-Allergy Safety Conflict Detector
    fun findDrugAllergyConflict(): Pair<String, String>? {
        val meds = extractedData?.optJSONArray("medications") ?: return null
        val patientAllergies = patientEntity?.allergies ?: ""
        val detectedAllergies = extractedData?.optJSONArray("allergies")
        val allAllergies = mutableListOf<String>()

        if (patientAllergies.contains("Penicillin", ignoreCase = true)) allAllergies.add("Penicillin")
        if (patientAllergies.contains("Sulfa", ignoreCase = true)) allAllergies.add("Sulfa")
        if (detectedAllergies != null) {
            for (i in 0 until detectedAllergies.length()) {
                val a = detectedAllergies.getString(i)
                if (!allAllergies.contains(a)) allAllergies.add(a)
            }
        }

        for (i in 0 until meds.length()) {
            val m = meds.getJSONObject(i)
            val name = m.optString("name", "")
            for (a in allAllergies) {
                if (a.equals("Penicillin", ignoreCase = true)) {
                    if (name.contains("Amoxicillin", ignoreCase = true) ||
                        name.contains("Augmentin", ignoreCase = true) ||
                        name.contains("Penicillin", ignoreCase = true) ||
                        name.contains("Ampicillin", ignoreCase = true)) {
                        return Pair("Penicillin", name)
                    }
                }
                if (a.equals("Sulfa", ignoreCase = true)) {
                    if (name.contains("Bactrim", ignoreCase = true) ||
                        name.contains("Septra", ignoreCase = true) ||
                        name.contains("Sulfa", ignoreCase = true)) {
                        return Pair("Sulfa", name)
                    }
                }
            }
        }
        return null
    }

    fun saveConsultationToLocalDb() {
        if (isSaving) return
        isSaving = true
        coroutineScope.launch {
            val activePatientId = if (!patientId.isNullOrBlank()) patientId else "PAT-WALKIN-${(1000..9999).random()}"
            val activeDoc = repository.getActiveDoctor()?.doctorId ?: "DOC-DEFAULT"

            val cleanStructuredJson = if (!assistEnabled && extractedJsonStr.isNotBlank()) {
                try {
                    val cleanObj = JSONObject(extractedJsonStr)
                    cleanObj.remove("aiSuggestions")
                    cleanObj.toString(2)
                } catch (e: Exception) {
                    extractedJsonStr
                }
            } else {
                extractedJsonStr
            }

            val cleanSuggestionsJson = if (assistEnabled) {
                extractedData?.optJSONObject("aiSuggestions")?.toString() ?: "{}"
            } else {
                "{}"
            }

            repository.saveConsultation(
                patientId = activePatientId,
                rawTranscript = editableTranscript,
                structuredDataJson = cleanStructuredJson,
                aiSuggestionsJson = cleanSuggestionsJson,
                doctorId = activeDoc
            )

            try {
                repository.triggerSync()
            } catch (e: Exception) {
                // Stored safely offline in Room DB sync_queue
            }

            isSaving = false
            onSaveSuccess()
        }
    }

    val sampleScript = """
Doctor: Haan bataiye, kya problem hai aapko?
Patient: Doctor sahab, mujhe teen din se bahut tez bukhar aa raha hai. Raat ko 102-103 tak chala jaata hai. Aur sir mein bahut dard ho raha hai.
Doctor: Aur koi problem? Khansi ya sardi?
Patient: Haan thodi khansi bhi hai, dry khansi hai. Aur gala bhi dukh raha hai. Ulti jaisa feel hota hai. Poore body mein dard hai, kamar aur tangon mein.
Doctor: Pehle se koi bimari? Regular medicine ya allergy?
Patient: Penicillin se allergy hai.
Doctor: Temperature check karte hain... 101.4 Fahrenheit hai. BP 130 over 85 hai. Pulse 92 bpm, oxygen 97%. Viral fever lag raha hai.
Doctor: Paracetamol 650mg, din mein teen baar, 5 din. Azithromycin 500mg, raat ko, 3 din. Pantoprazole 40mg, subah khali pet, 5 din. Betadine gargle garam paani mein.
Doctor: CBC aur Dengue NS1 test karwao. 3 din mein bukhar nahi utra toh wapas aana.
    """.trimIndent()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Active Consultation", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Slate900)
                        Text(patientEntity?.name ?: patientId ?: "OPD Patient Walk-In", fontSize = 12.sp, color = Sky600)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Slate700)
                    }
                },
                actions = {
                    // Doctor AI Assist Toggle with active chip indicator
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (assistEnabled) Indigo50 else Slate100,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (assistEnabled) Indigo500.copy(alpha = 0.5f) else Slate200
                        ),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = if (assistEnabled) Indigo600 else Slate400,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (assistEnabled) "Assist ON" else "Assist OFF",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (assistEnabled) Indigo700 else Slate600
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Switch(
                                checked = assistEnabled,
                                onCheckedChange = { isChecked ->
                                    assistEnabled = isChecked
                                    if (!isChecked) {
                                        // Purge suggestions from memory immediately
                                        extractedData?.let { current ->
                                            current.remove("aiSuggestions")
                                            extractedData = JSONObject(current.toString())
                                            extractedJsonStr = current.toString(2)
                                        }
                                    } else {
                                        if (editableTranscript.isNotBlank()) {
                                            coroutineScope.launch {
                                                isProcessingExtraction = true
                                                val json = gemmaEngine.extractMedicalData(editableTranscript, true)
                                                extractedJsonStr = json
                                                extractedData = JSONObject(json)
                                                isProcessingExtraction = false
                                            }
                                        }
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Indigo600,
                                    uncheckedThumbColor = Color.White,
                                    uncheckedTrackColor = Slate300
                                ),
                                modifier = Modifier.height(24.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Slate50)
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shadowElevation = 8.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, Slate200)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        val medCount = extractedData?.optJSONArray("medications")?.length() ?: 0
                        val diagCount = extractedData?.optJSONArray("diagnosis")?.length() ?: 0
                        Text(
                            if (medCount > 0 || diagCount > 0) "$medCount Meds • $diagCount Diag" else "Draft Consultation",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate900
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Emerald600)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Offline Encrypted DB",
                                fontSize = 10.sp,
                                color = Emerald600,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Button(
                        onClick = {
                            val conflict = findDrugAllergyConflict()
                            if (conflict != null && !allergyOverrideConfirmed) {
                                conflictingAllergyName = conflict.first
                                conflictingDrugName = conflict.second
                                showAllergyConfirmDialog = true
                            } else {
                                saveConsultationToLocalDb()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        enabled = !isSaving
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Saving...", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save Consultation", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        containerColor = Slate50
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp)
        ) {
            // 0. Patient OPD Context Header Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Sky100),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    tint = Sky600,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    patientEntity?.name ?: patientId ?: "OPD Patient Walk-In",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate900
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                val metaText = buildString {
                                    if (patientEntity != null && patientEntity!!.age > 0) append("${patientEntity!!.age} Yrs")
                                    if (patientEntity != null && patientEntity!!.gender.isNotBlank()) {
                                        if (isNotEmpty()) append(" • ")
                                        append(patientEntity!!.gender.replaceFirstChar { it.uppercase() })
                                    }
                                    if (patientEntity != null && patientEntity!!.bloodGroup.isNotBlank()) {
                                        if (isNotEmpty()) append(" • ")
                                        append(patientEntity!!.bloodGroup)
                                    }
                                    if (isEmpty()) append("ID: ${patientId ?: "OPD-WALKIN"}")
                                }
                                Text(metaText, fontSize = 12.sp, color = Slate500, fontWeight = FontWeight.Medium)
                            }
                        }

                        val patientAllergies = patientEntity?.allergies ?: ""
                        val hasPatientAllergies = patientAllergies.isNotBlank() && patientAllergies != "[]"
                        if (hasPatientAllergies) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Rose50,
                                border = androidx.compose.foundation.BorderStroke(1.dp, Rose500.copy(alpha = 0.35f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        "Allergy: $patientAllergies",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Rose500
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 1. Microphone Voice Capture & Waveform Visualizer
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = if (isRecording) Rose50.copy(alpha = 0.3f) else Color.White),
                    border = androidx.compose.foundation.BorderStroke(
                        1.2.dp,
                        if (isRecording) Rose500.copy(alpha = 0.4f) else Slate200
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Header Status Strip
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isRecording) Rose500 else Emerald600)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    if (isRecording) "Recording Active..." else "Whisper Offline ASR",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isRecording) Rose500 else Slate700
                                )
                            }

                            Surface(shape = RoundedCornerShape(6.dp), color = Slate100) {
                                Text(
                                    detectedLanguage,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    fontSize = 11.sp,
                                    color = Slate600,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Mic Action Button
                        Box(
                            modifier = Modifier
                                .size(70.dp)
                                .clip(CircleShape)
                                .background(if (isRecording) Rose500 else Sky600)
                                .clickable {
                                    if (!isRecording) {
                                        val hasPermission = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.RECORD_AUDIO
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (hasPermission) {
                                            whisperEngine.startListening()
                                        } else {
                                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    } else {
                                        val transcript = whisperEngine.stopListening()
                                        if (transcript.isNotBlank()) {
                                            editableTranscript = transcript
                                            coroutineScope.launch {
                                                isProcessingExtraction = true
                                                val json = gemmaEngine.extractMedicalData(transcript, assistEnabled)
                                                extractedJsonStr = json
                                                extractedData = JSONObject(json)
                                                isProcessingExtraction = false
                                            }
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = if (isRecording) "Stop Recording" else "Start Recording",
                                tint = Color.White,
                                modifier = Modifier.size(34.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            if (isRecording) "Listening... Tap to stop & extract" else "Tap Mic to Start Consultation",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = if (isRecording) Rose500 else Slate800
                        )

                        // Live Waveform Visualizer
                        if (isRecording) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.height(24.dp)
                            ) {
                                val bars = 11
                                for (i in 0 until bars) {
                                    val heightFactor = ((audioAmplitude * 24) + ((i % 3) * 5) + 4).coerceIn(4f, 24f)
                                    Box(
                                        modifier = Modifier
                                            .width(3.5.dp)
                                            .height(heightFactor.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(Rose500)
                                    )
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Speak in Hindi, English, or Hinglish with patient",
                                fontSize = 11.sp,
                                color = Slate400
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Load Clinical Demo Button
                        OutlinedButton(
                            onClick = {
                                whisperEngine.setManualTranscript(sampleScript)
                                editableTranscript = sampleScript
                                coroutineScope.launch {
                                    isProcessingExtraction = true
                                    val json = gemmaEngine.extractMedicalData(sampleScript, assistEnabled)
                                    extractedJsonStr = json
                                    extractedData = JSONObject(json)
                                    isProcessingExtraction = false
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Science, contentDescription = null, tint = Sky600, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Load Clinical Demo Case", fontSize = 12.sp, color = Slate700, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            // 2. Real-Time Spoken Transcript Box
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Slate200)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, tint = Sky600, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Dialogue Transcript", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Slate800)
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (showManualInput) Sky50 else Slate100,
                                modifier = Modifier.clickable { showManualInput = !showManualInput }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        if (showManualInput) Icons.Default.Check else Icons.Default.Edit,
                                        contentDescription = null,
                                        tint = if (showManualInput) Sky600 else Slate600,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        if (showManualInput) "Done" else "Edit Text",
                                        fontSize = 11.sp,
                                        color = if (showManualInput) Sky600 else Slate600,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (showManualInput) {
                            OutlinedTextField(
                                value = editableTranscript,
                                onValueChange = { editableTranscript = it },
                                placeholder = { Text("Speak or paste conversation text...", fontSize = 13.sp, color = Slate400) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 100.dp, max = 160.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = cleanTextFieldColors(),
                                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, lineHeight = 18.sp)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Button(
                                onClick = {
                                    if (editableTranscript.isNotBlank()) {
                                        coroutineScope.launch {
                                            isProcessingExtraction = true
                                            val json = gemmaEngine.extractMedicalData(editableTranscript, assistEnabled)
                                            extractedJsonStr = json
                                            extractedData = JSONObject(json)
                                            isProcessingExtraction = false
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Slate900)
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Extract Notes with Gemma 2B", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Slate50,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    if (editableTranscript.isNotBlank()) editableTranscript
                                    else "Real-time doctor and patient conversation will appear here automatically...",
                                    fontSize = 12.sp,
                                    color = if (editableTranscript.isNotBlank()) Slate700 else Slate400,
                                    lineHeight = 18.sp,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Extraction Loading Spinner
            if (isProcessingExtraction) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Indigo50),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Indigo500.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = Indigo600)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Gemma 2B Extracting Structured Notes...", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Indigo700)
                                Text("Normalizing Hindi/Marathi terms into English clinical record", fontSize = 11.sp, color = Slate600)
                            }
                        }
                    }
                }
            }

            // 3. Extracted Structured Clinical Record Output
            extractedData?.let { data ->
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.MedicalServices, contentDescription = null, tint = Sky600, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Clinical Record (Extracted)", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Slate900)
                        }
                        Surface(shape = RoundedCornerShape(6.dp), color = Indigo50) {
                            Text("Gemma 2B Offline", modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), fontSize = 10.sp, color = Indigo600, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Chief Complaint & Vitals (Responsive Grid & Doctor Quick-Edit)
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Slate200)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Chief Complaint Banner
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Sky50,
                                border = androidx.compose.foundation.BorderStroke(1.dp, Sky100),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(Sky100),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.LocalHospital, contentDescription = null, tint = Sky600, modifier = Modifier.size(18.dp))
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text("CHIEF COMPLAINT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Sky600, letterSpacing = 0.5.sp)
                                        Text(
                                            data.optString("chiefComplaint", "Medical Consultation"),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Slate900
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Vitals Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Recorded Vitals", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate800)

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Sky50,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Sky100),
                                    modifier = Modifier.clickable { showAddVitalsDialog = true }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = null, tint = Sky600, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            "Record Vitals",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Sky600
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            val vitals = data.optJSONObject("vitals")
                            val temp = vitals?.optString("temperature")?.takeIf { it.isNotBlank() && it != "null" }
                            val bp = vitals?.optString("bp")?.takeIf { it.isNotBlank() && it != "null" }
                            val pulse = vitals?.optString("pulse")?.takeIf { it.isNotBlank() && it != "null" }
                            val spo2 = vitals?.optString("spo2")?.takeIf { it.isNotBlank() && it != "null" }
                            val weight = vitals?.optString("weight")?.takeIf { it.isNotBlank() && it != "null" && it != "Not recorded" }
                            val hasAnyVital = temp != null || bp != null || pulse != null || spo2 != null || weight != null

                            if (hasAnyVital) {
                                // Clean 2-column Grid of Vitals
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    VitalGridTile(
                                        label = "Temperature",
                                        value = temp ?: "--",
                                        isWarning = temp?.contains("10") == true,
                                        icon = { Icon(Icons.Default.Thermostat, contentDescription = null, tint = if (temp?.contains("10") == true) Rose500 else Sky600, modifier = Modifier.size(16.dp)) },
                                        modifier = Modifier.weight(1f)
                                    )
                                    VitalGridTile(
                                        label = "Blood Pressure",
                                        value = bp ?: "--",
                                        icon = { Icon(Icons.Default.Speed, contentDescription = null, tint = Sky600, modifier = Modifier.size(16.dp)) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    VitalGridTile(
                                        label = "Pulse Rate",
                                        value = pulse ?: "--",
                                        icon = { Icon(Icons.Default.Favorite, contentDescription = null, tint = Rose500, modifier = Modifier.size(16.dp)) },
                                        modifier = Modifier.weight(1f)
                                    )
                                    VitalGridTile(
                                        label = "Oxygen (SpO2)",
                                        value = spo2 ?: "--",
                                        icon = { Icon(Icons.Default.Air, contentDescription = null, tint = Sky600, modifier = Modifier.size(16.dp)) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                if (weight != null) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(modifier = Modifier.fillMaxWidth()) {
                                        VitalGridTile(
                                            label = "Body Weight",
                                            value = weight,
                                            icon = { Icon(Icons.Default.FitnessCenter, contentDescription = null, tint = Slate600, modifier = Modifier.size(16.dp)) },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Slate50,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Info, contentDescription = null, tint = Sky600, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "No vitals detected in spoken note. Tap 'Record Vitals' to add physically.",
                                            fontSize = 11.sp,
                                            color = Slate600,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            // Allergies Alert (Clean badge without warning icon)
                            val allergies = data.optJSONArray("allergies")
                            val hasPatientAllergy = patientEntity?.allergies?.isNotBlank() == true && patientEntity?.allergies != "[]"
                            if ((allergies != null && allergies.length() > 0) || hasPatientAllergy) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Rose50,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Rose500.copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text("DOCUMENTED ALLERGIES", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Rose500, letterSpacing = 0.4.sp)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            if (allergies != null && allergies.length() > 0) {
                                                for (i in 0 until allergies.length()) {
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = Color.White,
                                                        border = androidx.compose.foundation.BorderStroke(0.8.dp, Rose500.copy(alpha = 0.25f))
                                                    ) {
                                                        Text(allergies.getString(i), modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), fontSize = 11.sp, color = Rose500, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            } else if (hasPatientAllergy) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = Color.White,
                                                    border = androidx.compose.foundation.BorderStroke(0.8.dp, Rose500.copy(alpha = 0.25f))
                                                ) {
                                                    Text(patientEntity?.allergies ?: "Penicillin", modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), fontSize = 11.sp, color = Rose500, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Flagged Issues & Missing Info Alert (Clean presentation without caution/info icons)
                            val flagged = data.optJSONArray("flaggedIssues")
                            val missing = data.optJSONArray("missingInfo")
                            val hasFlagsOrMissing = (flagged != null && flagged.length() > 0) || (missing != null && missing.length() > 0)
                            if (hasFlagsOrMissing) {
                                Spacer(modifier = Modifier.height(10.dp))
                                if (flagged != null && flagged.length() > 0) {
                                    for (i in 0 until flagged.length()) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Amber50,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Amber500.copy(alpha = 0.25f)),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 2.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("•", fontSize = 14.sp, color = Amber500, fontWeight = FontWeight.Bold)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(flagged.getString(i), fontSize = 11.sp, color = Amber500, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    }
                                }
                                if (missing != null && missing.length() > 0) {
                                    for (i in 0 until missing.length()) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Slate50,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 2.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("•", fontSize = 14.sp, color = Slate400, fontWeight = FontWeight.Bold)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(missing.getString(i), fontSize = 11.sp, color = Slate600, fontWeight = FontWeight.Normal)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Extracted Symptoms & Pertinent Negatives Card
                val symptoms = data.optJSONArray("symptoms")
                val pertinentNegs = data.optJSONArray("pertinentNegatives")
                if ((symptoms != null && symptoms.length() > 0) || (pertinentNegs != null && pertinentNegs.length() > 0)) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Slate200)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                if (symptoms != null && symptoms.length() > 0) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Symptoms & History (HPI)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate800)
                                        Surface(shape = RoundedCornerShape(6.dp), color = Amber50) {
                                            Text("${symptoms.length()} Noted", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp, color = Amber500, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    for (i in 0 until symptoms.length()) {
                                        val s = symptoms.getJSONObject(i)
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = Slate50,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 3.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(10.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(s.optString("name"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Slate800)
                                                    val notes = s.optString("notes")
                                                    if (notes.isNotBlank()) {
                                                        Text(notes, fontSize = 11.sp, color = Slate500)
                                                    }
                                                }
                                                Surface(shape = RoundedCornerShape(6.dp), color = Amber50) {
                                                    Text(
                                                        s.optString("duration", "Recent"),
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                        fontSize = 10.sp,
                                                        color = Amber500,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Pertinent Negatives inside same clinical evaluation card
                                if (pertinentNegs != null && pertinentNegs.length() > 0) {
                                    if (symptoms != null && symptoms.length() > 0) {
                                        Spacer(modifier = Modifier.height(14.dp))
                                        HorizontalDivider(color = Slate100, thickness = 1.dp)
                                        Spacer(modifier = Modifier.height(12.dp))
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Pertinent Negatives (Ruled Out)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate800)
                                        Surface(shape = RoundedCornerShape(6.dp), color = Emerald50) {
                                            Text("Excluded", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp, color = Emerald600, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    for (i in 0 until pertinentNegs.length()) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(vertical = 3.dp)
                                        ) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = Emerald600, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(pertinentNegs.getString(i), fontSize = 12.sp, color = Slate700, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Clinical Diagnosis (Stated by Doctor / Quick-Add)
                val diagnoses = data.optJSONArray("diagnosis")
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Slate200)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Clinical Diagnosis", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate800)

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Sky50,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Sky100),
                                    modifier = Modifier.clickable { showAddDiagnosisDialog = true }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, tint = Sky600, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            "Add Diagnosis",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Sky600
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            if (diagnoses != null && diagnoses.length() > 0) {
                                for (i in 0 until diagnoses.length()) {
                                    val diag = diagnoses.getString(i)
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Sky50.copy(alpha = 0.5f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Sky100),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                        ) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Sky600, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(diag, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate900)
                                        }
                                    }
                                }
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Slate50,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.HourglassEmpty, contentDescription = null, tint = Slate400, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("No diagnosis stated in voice note. Tap '+ Add Diagnosis' if needed.", fontSize = 11.sp, color = Slate500, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }
                }

                // Prescribed Medications (With Offline Allergy Blocker & Quick-Add)
                val meds = data.optJSONArray("medications")
                val allergyConflict = findDrugAllergyConflict()

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Slate200)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Prescription Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Medication, contentDescription = null, tint = Sky600, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Prescription (Rx)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate800)
                                    if (meds != null && meds.length() > 0) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(shape = RoundedCornerShape(6.dp), color = Sky50) {
                                            Text(
                                                "${meds.length()} Item${if (meds.length() > 1) "s" else ""}",
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                fontSize = 10.sp,
                                                color = Sky600,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Sky50,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Sky100),
                                    modifier = Modifier.clickable { showAddMedicineDialog = true }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, tint = Sky600, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            "Add Medicine",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Sky600
                                        )
                                    }
                                }
                            }

                            // Allergy Conflict Alert Banner (Clean banner without warning icon)
                            if (allergyConflict != null) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Rose50,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Rose500.copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                        Text(
                                            "CONTRAINDICATION DETECTED",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Rose500,
                                            letterSpacing = 0.5.sp
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            "${allergyConflict.second} conflicts with documented ${allergyConflict.first} allergy!",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Rose500,
                                            lineHeight = 15.sp
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            if (meds != null && meds.length() > 0) {
                                for (i in 0 until meds.length()) {
                                    val m = meds.getJSONObject(i)
                                    val drugName = m.optString("name")
                                    val isContraindicated = allergyConflict != null && drugName.equals(allergyConflict.second, ignoreCase = true)

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isContraindicated) Rose50.copy(alpha = 0.5f) else Slate50,
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (isContraindicated) Rose500.copy(alpha = 0.4f) else Slate200
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            // Top Row: Rx Badge, Drug Name, Dosage & Delete Action
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Surface(
                                                        shape = RoundedCornerShape(5.dp),
                                                        color = if (isContraindicated) Rose500.copy(alpha = 0.15f) else Sky100
                                                    ) {
                                                        Text(
                                                            "Rx ${i + 1}",
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (isContraindicated) Rose500 else Sky600,
                                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        drugName,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp,
                                                        color = if (isContraindicated) Rose500 else Slate900
                                                    )
                                                    val dosage = m.optString("dosage")
                                                    if (dosage.isNotBlank() && dosage != "null") {
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Surface(
                                                            shape = RoundedCornerShape(4.dp),
                                                            color = if (isContraindicated) Rose50 else Sky100
                                                        ) {
                                                            Text(
                                                                dosage,
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = if (isContraindicated) Rose500 else Sky600,
                                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    }
                                                }

                                                IconButton(
                                                    onClick = {
                                                        val updatedArray = JSONArray()
                                                        for (j in 0 until meds.length()) {
                                                            if (j != i) updatedArray.put(meds.getJSONObject(j))
                                                        }
                                                        data.put("medications", updatedArray)
                                                        extractedData = JSONObject(data.toString())
                                                        extractedJsonStr = data.toString(2)
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.DeleteOutline,
                                                        contentDescription = "Remove drug",
                                                        tint = Slate400,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))

                                            // Middle Row: Frequency & Duration Tags
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = Sky50,
                                                    border = androidx.compose.foundation.BorderStroke(0.8.dp, Sky100)
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                                    ) {
                                                        Icon(Icons.Default.Schedule, contentDescription = null, tint = Sky600, modifier = Modifier.size(11.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            m.optString("frequency", "As directed"),
                                                            fontSize = 10.sp,
                                                            color = Sky600,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                    }
                                                }

                                                val dur = m.optString("duration")
                                                if (dur.isNotBlank() && dur != "null") {
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = Slate100,
                                                        border = androidx.compose.foundation.BorderStroke(0.8.dp, Slate200)
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                                        ) {
                                                            Icon(Icons.Default.DateRange, contentDescription = null, tint = Slate600, modifier = Modifier.size(11.dp))
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text(
                                                                dur,
                                                                fontSize = 10.sp,
                                                                color = Slate700,
                                                                fontWeight = FontWeight.Medium
                                                            )
                                                        }
                                                    }
                                                }

                                                if (isContraindicated) {
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = Rose50,
                                                        border = androidx.compose.foundation.BorderStroke(0.8.dp, Rose500.copy(alpha = 0.3f))
                                                    ) {
                                                        Text(
                                                            "Allergy Risk",
                                                            color = Rose500,
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            // Instructions / Special Advice Row
                                            val instr = m.optString("instructions")
                                            if (instr.isNotBlank() && instr != "null") {
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = Color.White,
                                                    border = androidx.compose.foundation.BorderStroke(0.6.dp, Slate200),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(Icons.Default.Description, contentDescription = null, tint = Slate400, modifier = Modifier.size(12.dp))
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(instr, fontSize = 11.sp, color = Slate600)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Slate50,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Medication, contentDescription = null, tint = Slate400, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("No medications prescribed in dialogue. Tap '+ Add Medicine' to prescribe.", fontSize = 11.sp, color = Slate500, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }
                }

                // Follow-Up & Care Advice (Modern clean Material icons)
                val followUp = data.optString("followUp")
                val additionalNotes = data.optString("additionalNotes")
                if (followUp.isNotBlank() || additionalNotes.isNotBlank()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Slate200)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AssignmentTurnedIn, contentDescription = null, tint = Emerald600, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Follow-up & Care Instructions", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate800)
                                }

                                if (followUp.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Sky50.copy(alpha = 0.5f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Sky100),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Sky100,
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(Icons.Default.Event, contentDescription = null, tint = Sky600, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text("FOLLOW-UP SCHEDULE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Sky600, letterSpacing = 0.4.sp)
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(followUp, fontSize = 12.sp, color = Slate900, fontWeight = FontWeight.Medium)
                                            }
                                        }
                                    }
                                }

                                if (additionalNotes.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Emerald50.copy(alpha = 0.5f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Emerald600.copy(alpha = 0.2f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Emerald50,
                                                border = androidx.compose.foundation.BorderStroke(1.dp, Emerald600.copy(alpha = 0.3f)),
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(Icons.Default.HealthAndSafety, contentDescription = null, tint = Emerald600, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text("PATIENT ADVICE & DIET", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Emerald600, letterSpacing = 0.4.sp)
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(additionalNotes, fontSize = 12.sp, color = Slate800, lineHeight = 17.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. DOCTOR ASSIST SUGGESTIONS (Differential Diagnoses, Diagnostics, Warnings, Interactions)
                // STRICT RULE: Render ONLY if assistEnabled is TRUE and aiSuggestions exists!
                val suggestions = data.optJSONObject("aiSuggestions")
                if (assistEnabled && suggestions != null) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Indigo50.copy(alpha = 0.6f)),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, Indigo500.copy(alpha = 0.35f))
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Psychology, contentDescription = null, tint = Indigo600, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Doctor Assist AI (Clinical Co-Pilot)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Indigo700)
                                    }
                                    Surface(shape = RoundedCornerShape(6.dp), color = Indigo100) {
                                        Text("Gemma 2B", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp, color = Indigo700, fontWeight = FontWeight.Bold)
                                    }
                                }

                                // 1. Possible Diagnoses (Differentials)
                                val diffDiag = suggestions.optJSONArray("possibleDiagnoses")
                                if (diffDiag != null && diffDiag.length() > 0) {
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text("POSSIBLE DIAGNOSES (DIFFERENTIALS)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Indigo700, letterSpacing = 0.5.sp)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    for (i in 0 until diffDiag.length()) {
                                        val item = diffDiag.getJSONObject(i)
                                        val confStr = item.optString("confidence", "Moderate")
                                        val isHigh = confStr.contains("high", ignoreCase = true)
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color.White,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Indigo100),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 3.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(item.optString("condition", "Condition"), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Slate900)
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = if (isHigh) Indigo50 else Sky50
                                                    ) {
                                                        Text(
                                                            confStr,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (isHigh) Indigo600 else Sky600
                                                        )
                                                    }
                                                }
                                                val reasoning = item.optString("reasoning")
                                                if (reasoning.isNotBlank()) {
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(reasoning, fontSize = 11.sp, color = Slate600, lineHeight = 16.sp)
                                                }
                                            }
                                        }
                                    }
                                }

                                // 2. Recommended Diagnostic Tests
                                val tests = suggestions.optJSONArray("recommendedTests")
                                if (tests != null && tests.length() > 0) {
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text("RECOMMENDED DIAGNOSTICS & LABS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Indigo700, letterSpacing = 0.5.sp)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    for (i in 0 until tests.length()) {
                                        val test = tests.getJSONObject(i)
                                        val urgency = test.optString("urgency", "ROUTINE")
                                        val isUrgent = urgency.contains("urgent", ignoreCase = true)
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color.White,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Indigo100),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 3.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(test.optString("test", "Test"), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Slate900)
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = if (isUrgent) Rose50 else Slate100
                                                    ) {
                                                        Text(
                                                            urgency.uppercase(),
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (isUrgent) Rose500 else Slate600
                                                        )
                                                    }
                                                }
                                                val reason = test.optString("reason")
                                                if (reason.isNotBlank()) {
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text("Reason: $reason", fontSize = 11.sp, color = Slate600, lineHeight = 16.sp)
                                                }
                                            }
                                        }
                                    }
                                }

                                // 3. Safety Warnings & Red Flags
                                val warnings = suggestions.optJSONArray("warnings")
                                if (warnings != null && warnings.length() > 0) {
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text("SAFETY WARNINGS & CONTRAINDICATIONS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Rose500, letterSpacing = 0.5.sp)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Rose50,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Rose500.copy(alpha = 0.3f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            for (i in 0 until warnings.length()) {
                                                Row(verticalAlignment = Alignment.Top) {
                                                    Text("•", fontSize = 14.sp, color = Rose500, fontWeight = FontWeight.Bold)
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        warnings.getString(i),
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = Rose500,
                                                        lineHeight = 16.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // 4. Drug-Drug Interactions
                                val drugInteractions = suggestions.optJSONArray("drugInteractions")
                                if (drugInteractions != null && drugInteractions.length() > 0) {
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text("DRUG-DRUG INTERACTIONS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Indigo700, letterSpacing = 0.5.sp)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    for (i in 0 until drugInteractions.length()) {
                                        val item = drugInteractions.getJSONObject(i)
                                        val severity = item.optString("severity", "None")
                                        val isSevere = severity.contains("severe", ignoreCase = true)
                                        val isMod = severity.contains("moderate", ignoreCase = true)
                                        val sevColor = if (isSevere) Rose500 else if (isMod) Amber500 else Emerald600
                                        val sevBg = if (isSevere) Rose50 else if (isMod) Amber50 else Emerald50

                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color.White,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Indigo100),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 3.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        "${item.optString("drug1")} + ${item.optString("drug2")}",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.sp,
                                                        color = Slate900
                                                    )
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = sevBg
                                                    ) {
                                                        Text(
                                                            severity,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = sevColor
                                                        )
                                                    }
                                                }
                                                val effect = item.optString("effect")
                                                if (effect.isNotBlank()) {
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(effect, fontSize = 11.sp, color = Slate600, lineHeight = 16.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ----------------------------------------------------
    // DIALOGS: Offline Clinical Checkpoints
    // ----------------------------------------------------

    // 1. Critical Allergy Contraindication Dialog
    if (showAllergyConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showAllergyConfirmDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Rose500)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Allergy Contraindication!", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Rose500)
                }
            },
            text = {
                Text(
                    "Patient has documented '$conflictingAllergyName' allergy, but '$conflictingDrugName' is in the prescription.\n\nDo you want to review and remove it, or override with doctor's clinical discretion?",
                    fontSize = 13.sp,
                    color = Slate700,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        allergyOverrideConfirmed = true
                        showAllergyConfirmDialog = false
                        saveConsultationToLocalDb()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Rose500)
                ) {
                    Text("Override & Save")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAllergyConfirmDialog = false }) {
                    Text("Review Prescription")
                }
            }
        )
    }

    // 2. Quick Edit / Add Vitals Dialog
    if (showAddVitalsDialog) {
        val vitalsObj = extractedData?.optJSONObject("vitals") ?: JSONObject()
        var tempInput by remember { mutableStateOf(vitalsObj.optString("temperature", "")) }
        var bpInput by remember { mutableStateOf(vitalsObj.optString("bp", "")) }
        var pulseInput by remember { mutableStateOf(vitalsObj.optString("pulse", "")) }
        var spo2Input by remember { mutableStateOf(vitalsObj.optString("spo2", "")) }
        var weightInput by remember { mutableStateOf(vitalsObj.optString("weight", "")) }

        AlertDialog(
            onDismissRequest = { showAddVitalsDialog = false },
            title = { Text("Record Measured Vitals", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Slate900) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter vitals measured physically during consultation:", fontSize = 12.sp, color = Slate500)
                    OutlinedTextField(
                        value = tempInput,
                        onValueChange = { tempInput = it },
                        label = { Text("Temperature (e.g. 101.4°F)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = cleanTextFieldColors(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = bpInput,
                        onValueChange = { bpInput = it },
                        label = { Text("Blood Pressure (e.g. 120/80 mmHg)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = cleanTextFieldColors(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = pulseInput,
                        onValueChange = { pulseInput = it },
                        label = { Text("Pulse (e.g. 84 bpm)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = cleanTextFieldColors(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = spo2Input,
                        onValueChange = { spo2Input = it },
                        label = { Text("SpO2 (e.g. 98%)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = cleanTextFieldColors(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = weightInput,
                        onValueChange = { weightInput = it },
                        label = { Text("Weight (e.g. 68 kg)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = cleanTextFieldColors(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val currentData = extractedData ?: JSONObject()
                        val updatedVitals = JSONObject().apply {
                            if (tempInput.isNotBlank()) put("temperature", if (!tempInput.contains("°")) "${tempInput.trim()}°F" else tempInput.trim())
                            if (bpInput.isNotBlank()) put("bp", if (!bpInput.contains("mmHg", ignoreCase = true)) "${bpInput.trim()} mmHg" else bpInput.trim())
                            if (pulseInput.isNotBlank()) put("pulse", if (!pulseInput.contains("bpm", ignoreCase = true)) "${pulseInput.trim()} bpm" else pulseInput.trim())
                            if (spo2Input.isNotBlank()) put("spo2", if (!spo2Input.contains("%")) "${spo2Input.trim()}%" else spo2Input.trim())
                            if (weightInput.isNotBlank()) put("weight", if (!weightInput.contains("kg", ignoreCase = true)) "${weightInput.trim()} kg" else weightInput.trim())
                        }
                        currentData.put("vitals", updatedVitals)
                        extractedData = JSONObject(currentData.toString())
                        extractedJsonStr = currentData.toString(2)
                        showAddVitalsDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Slate900)
                ) {
                    Text("Save Vitals")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAddVitalsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 3. Quick Add Diagnosis Dialog
    if (showAddDiagnosisDialog) {
        var diagInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDiagnosisDialog = false },
            title = { Text("Add Clinical Diagnosis", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Slate900) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter doctor's confirmed impression or ICD diagnosis:", fontSize = 12.sp, color = Slate500)
                    OutlinedTextField(
                        value = diagInput,
                        onValueChange = { diagInput = it },
                        label = { Text("Diagnosis (e.g. Acute Bronchitis)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = cleanTextFieldColors(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (diagInput.isNotBlank()) {
                            val currentData = extractedData ?: JSONObject()
                            val currentDiag = currentData.optJSONArray("diagnosis") ?: JSONArray()
                            currentDiag.put(diagInput.trim())
                            currentData.put("diagnosis", currentDiag)
                            extractedData = JSONObject(currentData.toString())
                            extractedJsonStr = currentData.toString(2)
                        }
                        showAddDiagnosisDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Slate900)
                ) {
                    Text("Add Diagnosis")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAddDiagnosisDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 4. Quick Add Medicine Dialog with Offline Clinical Lexicon Auto-Complete
    if (showAddMedicineDialog) {
        var drugNameInput by remember { mutableStateOf("") }
        var dosageInput by remember { mutableStateOf("") }
        var freqInput by remember { mutableStateOf("Three times daily (TDS)") }
        var durInput by remember { mutableStateOf("5 days") }
        var instrInput by remember { mutableStateOf("Take after meals with water") }

        val suggestions = remember(drugNameInput) {
            if (drugNameInput.trim().length >= 2) {
                ClinicalDrugLexicon.searchDrugs(drugNameInput, limit = 4)
            } else {
                emptyList()
            }
        }

        AlertDialog(
            onDismissRequest = { showAddMedicineDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Medication, contentDescription = null, tint = Sky600, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Prescription Drug", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Slate900)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = drugNameInput,
                        onValueChange = { drugNameInput = it },
                        label = { Text("Search Drug (e.g. Augmentin, Dolo, Pan)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = cleanTextFieldColors(),
                        singleLine = true
                    )

                    // Auto-complete suggestion chips / rows from Offline Drug Lexicon
                    if (suggestions.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Sky50.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                .border(1.dp, Sky100, RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            Text(
                                "Quick Suggestions (Tap to Auto-fill):",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Sky600
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            suggestions.forEach { drug ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable {
                                            drugNameInput = "${drug.brandName} (${drug.genericName})"
                                            dosageInput = drug.standardDosage
                                            freqInput = drug.defaultFrequency
                                            durInput = drug.defaultDuration
                                            instrInput = drug.instructions
                                        }
                                        .padding(vertical = 4.dp, horizontal = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(drug.brandName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate800)
                                        Text(drug.genericName, fontSize = 10.sp, color = Slate500, maxLines = 1)
                                    }
                                    Surface(shape = RoundedCornerShape(4.dp), color = Sky100) {
                                        Text(drug.standardDosage, fontSize = 10.sp, color = Sky600, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                    }
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = dosageInput,
                        onValueChange = { dosageInput = it },
                        label = { Text("Dosage (e.g. 650mg, 625mg)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = cleanTextFieldColors(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = freqInput,
                        onValueChange = { freqInput = it },
                        label = { Text("Frequency (e.g. TDS / BD / OD / SOS)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = cleanTextFieldColors(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = durInput,
                        onValueChange = { durInput = it },
                        label = { Text("Duration (e.g. 5 days, 3 days)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = cleanTextFieldColors(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = instrInput,
                        onValueChange = { instrInput = it },
                        label = { Text("Instructions (e.g. After meals)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = cleanTextFieldColors(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (drugNameInput.isNotBlank()) {
                            val currentData = extractedData ?: JSONObject()
                            val currentMeds = currentData.optJSONArray("medications") ?: JSONArray()
                            currentMeds.put(JSONObject().apply {
                                put("name", drugNameInput.trim())
                                put("dosage", dosageInput.trim().ifBlank { "Standard" })
                                put("frequency", freqInput.trim())
                                put("duration", durInput.trim())
                                put("route", "oral")
                                put("instructions", instrInput.trim())
                            })
                            currentData.put("medications", currentMeds)
                            extractedData = JSONObject(currentData.toString())
                            extractedJsonStr = currentData.toString(2)
                        }
                        showAddMedicineDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Slate900)
                ) {
                    Text("Add Drug")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAddMedicineDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun VitalGridTile(
    label: String,
    value: String,
    icon: @Composable () -> Unit,
    isWarning: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isWarning) Amber50.copy(alpha = 0.7f) else Slate50,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isWarning) Amber500.copy(alpha = 0.5f) else Slate200
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    label.uppercase(),
                    fontSize = 10.sp,
                    color = if (isWarning) Amber500 else Slate400,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.4.sp
                )
                icon()
            }
            Text(
                value,
                fontSize = 14.sp,
                color = if (isWarning) Slate900 else Slate800,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun VitalBadge(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Slate50,
        border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
        modifier = Modifier.height(48.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(label, fontSize = 10.sp, color = Slate400, fontWeight = FontWeight.SemiBold)
            Text(value, fontSize = 12.sp, color = Slate800, fontWeight = FontWeight.Bold)
        }
    }
}

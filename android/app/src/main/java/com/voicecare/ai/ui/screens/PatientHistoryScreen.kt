package com.voicecare.ai.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicecare.ai.data.local.entities.ConsultationEntity
import com.voicecare.ai.data.local.entities.PatientEntity
import com.voicecare.ai.data.repository.VoiceCareRepository
import com.voicecare.ai.ui.theme.*
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientHistoryScreen(
    patientId: String,
    repository: VoiceCareRepository,
    onBackClick: () -> Unit
) {
    var patient by remember { mutableStateOf<PatientEntity?>(null) }
    val consultations by repository.getConsultationsForPatient(patientId).collectAsState(initial = emptyList())

    LaunchedEffect(patientId) {
        patient = repository.getPatientById(patientId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Patient Clinical History", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Slate900)
                        Text(
                            "${patient?.name ?: "Patient"} • $patientId",
                            fontSize = 12.sp,
                            color = Sky600,
                            fontWeight = FontWeight.Medium
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Slate700)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Slate50)
            )
        },
        containerColor = Slate50
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp)
        ) {
            // 1. Patient Demographics & Summary Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Avatar
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Sky100),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    (patient?.name?.take(1) ?: "P").uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    color = Sky600
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    patient?.name ?: "Patient Record",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = Slate900
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    "ID: $patientId" + if (!patient?.phone.isNullOrBlank()) " • 📞 ${patient?.phone}" else "",
                                    fontSize = 12.sp,
                                    color = Slate500
                                )
                            }

                            // Blood Group Badge
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Sky50,
                                border = androidx.compose.foundation.BorderStroke(1.dp, Sky100)
                            ) {
                                Text(
                                    patient?.bloodGroup?.ifBlank { "N/A" } ?: "N/A",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontSize = 12.sp,
                                    color = Sky600,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Quick info chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Slate50,
                                border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                    Text("AGE / GENDER", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Slate400)
                                    Text(
                                        "${patient?.age ?: "--"} yrs • ${patient?.gender?.replaceFirstChar { it.uppercase() } ?: "--"}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Slate800
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Slate50,
                                border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                    Text("TOTAL VISITS", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Slate400)
                                    Text(
                                        "${consultations.size} Encounter${if (consultations.size != 1) "s" else ""}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Slate800
                                    )
                                }
                            }
                        }

                        val rawAllergies = patient?.allergies?.trim() ?: ""
                        val cleanAllergies = remember(rawAllergies) {
                            if (rawAllergies.isBlank() || rawAllergies == "[]" || rawAllergies.equals("none", ignoreCase = true) || rawAllergies.equals("null", ignoreCase = true)) {
                                ""
                            } else {
                                rawAllergies.removePrefix("[").removeSuffix("]").replace("\"", "").trim()
                            }
                        }

                        if (cleanAllergies.isNotBlank()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Rose50,
                                border = androidx.compose.foundation.BorderStroke(1.dp, Rose500.copy(alpha = 0.25f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Shield,
                                        contentDescription = null,
                                        tint = Rose500,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "DOCUMENTED ALLERGIES:",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Rose500,
                                        letterSpacing = 0.4.sp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        cleanAllergies,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Rose500
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. Timeline Section Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Timeline, contentDescription = null, tint = Sky600, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Clinical Timeline & Encounters", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Slate800)
                    }

                    Surface(shape = RoundedCornerShape(6.dp), color = Slate100) {
                        Text(
                            "${consultations.size} Recorded",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            fontSize = 11.sp,
                            color = Slate600,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // 3. Encounters List or Empty State
            if (consultations.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Slate200)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(Slate100),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.MedicalServices, contentDescription = null, tint = Slate400, modifier = Modifier.size(28.dp))
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("No Consultation Records Yet", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Slate800)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Start a consultation to capture voice notes, vitals, diagnosis, and medications for this patient.",
                                fontSize = 12.sp,
                                color = Slate500,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                lineHeight = 17.sp
                            )
                        }
                    }
                }
            } else {
                items(consultations) { consult ->
                    ConsultationTimelineCard(consult)
                }
            }
        }
    }
}

@Composable
fun ConsultationTimelineCard(consult: ConsultationEntity) {
    var isTranscriptExpanded by remember { mutableStateOf(false) }

    val dateStr = remember(consult.consultationDate) {
        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        sdf.format(Date(consult.consultationDate))
    }

    val parsedJson = remember(consult.structuredDataJson) {
        try {
            JSONObject(consult.structuredDataJson)
        } catch (e: Exception) {
            JSONObject()
        }
    }

    val chiefComplaint = parsedJson.optString("chiefComplaint", "Medical Consultation")
    val diagnosis = remember(parsedJson) {
        parsedJson.optJSONArray("diagnosis")?.let { arr ->
            (0 until arr.length()).map { arr.getString(it) }
        } ?: emptyList()
    }

    val medications = remember(parsedJson) {
        parsedJson.optJSONArray("medications")?.let { arr ->
            (0 until arr.length()).map { i -> arr.getJSONObject(i) }
        } ?: emptyList()
    }

    val vitalsObj = parsedJson.optJSONObject("vitals")
    val temp = vitalsObj?.optString("temperature")?.takeIf { it.isNotBlank() && it != "null" }
    val bp = vitalsObj?.optString("bp")?.takeIf { it.isNotBlank() && it != "null" }
    val pulse = vitalsObj?.optString("pulse")?.takeIf { it.isNotBlank() && it != "null" }
    val spo2 = vitalsObj?.optString("spo2")?.takeIf { it.isNotBlank() && it != "null" }
    val hasVitals = temp != null || bp != null || pulse != null || spo2 != null

    val followUp = parsedJson.optString("followUp").takeIf { it.isNotBlank() && it != "null" }
    val additionalNotes = parsedJson.optString("additionalNotes").takeIf { it.isNotBlank() && it != "null" }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header: Date, Doctor, and Sync Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null, tint = Sky600, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(dateStr, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate800)
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (consult.syncStatus == "synced") Emerald50 else Amber50,
                    border = androidx.compose.foundation.BorderStroke(
                        0.8.dp,
                        if (consult.syncStatus == "synced") Emerald600.copy(alpha = 0.3f) else Amber500.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (consult.syncStatus == "synced") Emerald600 else Amber500)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            if (consult.syncStatus == "synced") "Synced" else "Offline Queue",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (consult.syncStatus == "synced") Emerald600 else Amber500
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Chief Complaint Box
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Sky50,
                border = androidx.compose.foundation.BorderStroke(1.dp, Sky100),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.LocalHospital, contentDescription = null, tint = Sky600, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("CHIEF COMPLAINT", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Sky600, letterSpacing = 0.4.sp)
                        Text(chiefComplaint, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate900)
                    }
                }
            }

            // Vitals Row (If present)
            if (hasVitals) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (temp != null) {
                        HistoryVitalChip("Temp", temp, modifier = Modifier.weight(1f))
                    }
                    if (bp != null) {
                        HistoryVitalChip("BP", bp, modifier = Modifier.weight(1f))
                    }
                    if (pulse != null) {
                        HistoryVitalChip("Pulse", pulse, modifier = Modifier.weight(1f))
                    }
                    if (spo2 != null) {
                        HistoryVitalChip("SpO2", spo2, modifier = Modifier.weight(1f))
                    }
                }
            }

            // Confirmed Diagnosis (If present)
            if (diagnosis.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text("CLINICAL DIAGNOSIS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Slate400, letterSpacing = 0.4.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    diagnosis.forEach { diag ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Indigo50,
                            border = androidx.compose.foundation.BorderStroke(0.8.dp, Indigo100)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Indigo600, modifier = Modifier.size(11.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(diag, fontSize = 11.sp, color = Indigo700, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Prescriptions (Rx) Section
            if (medications.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("PRESCRIBED MEDICATIONS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Slate400, letterSpacing = 0.4.sp)
                    Text("${medications.size} Item${if (medications.size > 1) "s" else ""}", fontSize = 10.sp, color = Slate500, fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.height(6.dp))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    medications.forEachIndexed { idx, m ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Slate50,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Sky100
                                        ) {
                                            Text(
                                                "Rx ${idx + 1}",
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Sky600
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(m.optString("name"), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate900)
                                    }

                                    val dosage = m.optString("dosage")
                                    if (dosage.isNotBlank() && dosage != "null") {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Slate100
                                        ) {
                                            Text(
                                                dosage,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Slate700
                                            )
                                        }
                                    }
                                }

                                val freq = m.optString("frequency")
                                val dur = m.optString("duration")
                                if (freq.isNotBlank() || dur.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (freq.isNotBlank() && freq != "null") {
                                            Text("• $freq", fontSize = 11.sp, color = Slate600)
                                        }
                                        if (dur.isNotBlank() && dur != "null") {
                                            Text("• $dur", fontSize = 11.sp, color = Slate600)
                                        }
                                    }
                                }

                                val instr = m.optString("instructions")
                                if (instr.isNotBlank() && instr != "null") {
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text("Instructions: $instr", fontSize = 10.sp, color = Slate500)
                                }
                            }
                        }
                    }
                }
            }

            // Follow-Up & Care Advice
            if (followUp != null || additionalNotes != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Emerald50.copy(alpha = 0.6f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Emerald600.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (followUp != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Event, contentDescription = null, tint = Emerald600, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Follow-up: $followUp", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Slate800)
                            }
                        }
                        if (additionalNotes != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.HealthAndSafety, contentDescription = null, tint = Emerald600, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(additionalNotes, fontSize = 11.sp, color = Slate700)
                            }
                        }
                    }
                }
            }

            // Expandable Spoken Transcript
            if (consult.rawTranscript.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { isTranscriptExpanded = !isTranscriptExpanded }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (isTranscriptExpanded) "Hide Spoken Consultation Audio Note" else "View Spoken Consultation Audio Note",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Sky600
                    )
                    Icon(
                        if (isTranscriptExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = Sky600,
                        modifier = Modifier.size(18.dp)
                    )
                }

                AnimatedVisibility(visible = isTranscriptExpanded) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Slate50,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                    ) {
                        Text(
                            consult.rawTranscript,
                            fontSize = 11.sp,
                            color = Slate600,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryVitalChip(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Slate50,
        border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label.uppercase(), fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Slate400)
            Text(value, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate800, maxLines = 1)
        }
    }
}

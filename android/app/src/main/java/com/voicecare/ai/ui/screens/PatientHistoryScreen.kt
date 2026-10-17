package com.voicecare.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                        Text("Universal Record across all doctors", fontSize = 12.sp, color = Indigo600)
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
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // Patient Demographics & Critical Flags Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Slate200)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                patient?.name ?: "Patient Record",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Slate900
                            )
                            Surface(shape = RoundedCornerShape(6.dp), color = Sky100) {
                                Text(
                                    patientId,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    fontSize = 11.sp,
                                    color = Sky600,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        val ageGender = "${patient?.age ?: 45} yrs • ${patient?.gender?.replaceFirstChar { it.uppercase() } ?: "Male"} • Blood Group: ${patient?.bloodGroup?.ifBlank { "B+" } ?: "B+"}"
                        Text(ageGender, fontSize = 13.sp, color = Slate600)

                        if (!patient?.phone.isNullOrBlank()) {
                            Text("Contact: ${patient?.phone}", fontSize = 12.sp, color = Slate400)
                        }

                        // Allergies warning
                        val allergiesStr = patient?.allergies ?: "[]"
                        if (allergiesStr.contains("Penicillin", ignoreCase = true) || allergiesStr.length > 4) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = Rose500, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Documented Allergies:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate700)
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(shape = RoundedCornerShape(4.dp), color = Rose50) {
                                    Text(
                                        if (allergiesStr.contains("Penicillin")) "Penicillin" else "Drug Allergies",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontSize = 11.sp,
                                        color = Rose500,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Clinical Timeline (All Doctor Encounters)", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Slate800)
                    Surface(shape = RoundedCornerShape(6.dp), color = Slate100) {
                        Text(
                            "${consultations.size} Encounters",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            fontSize = 11.sp,
                            color = Slate600,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (consultations.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Slate200)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.MedicalServices, contentDescription = null, tint = Slate400, modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No previous recorded consultations yet.", fontSize = 13.sp, color = Slate500)
                            Text("Start a consultation to record voice notes and prescriptions.", fontSize = 11.sp, color = Slate400)
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
    val diagnosis = parsedJson.optJSONArray("diagnosis")?.let { arr ->
        (0 until arr.length()).map { arr.getString(it) }
    } ?: emptyList()

    val medications = parsedJson.optJSONArray("medications")?.let { arr ->
        (0 until arr.length()).map { i ->
            val m = arr.getJSONObject(i)
            "${m.optString("name")} ${m.optString("dosage")} (${m.optString("frequency")})"
        }
    } ?: emptyList()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Slate200)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        if (consult.doctorId.isNotBlank()) "Doctor: ${consult.doctorId}" else "OPD Doctor",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Slate900
                    )
                    Text(dateStr, fontSize = 11.sp, color = Slate400)
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (consult.syncStatus == "synced") Emerald50 else Amber50
                ) {
                    Text(
                        if (consult.syncStatus == "synced") "Synced" else "Pending Sync",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (consult.syncStatus == "synced") Emerald600 else Amber500
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text("Chief Complaint:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate400)
            Text(chiefComplaint, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Slate800)

            if (diagnosis.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Diagnosis: ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate400)
                    Text(diagnosis.joinToString(", "), fontSize = 12.sp, color = Sky600, fontWeight = FontWeight.SemiBold)
                }
            }

            if (medications.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text("Prescription:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate400)
                Column(modifier = Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    medications.forEach { med ->
                        Text("• $med", fontSize = 12.sp, color = Slate700)
                    }
                }
            }
        }
    }
}

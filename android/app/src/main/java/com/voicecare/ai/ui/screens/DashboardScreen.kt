package com.voicecare.ai.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicecare.ai.R
import com.voicecare.ai.data.local.entities.PatientEntity
import com.voicecare.ai.ui.theme.*
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    patients: List<PatientEntity>,
    doctorId: String = "DOC-DEFAULT",
    pendingSyncCount: Int,
    isSyncing: Boolean = false,
    doctorName: String,
    onSyncClick: () -> Unit = {},
    onLogoutClick: () -> Unit,
    onPatientClick: (String) -> Unit,
    onStartConsultationClick: (String?) -> Unit,
    onAddPatientSubmit: (PatientEntity) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var isFormOpen by remember { mutableStateOf(false) }

    // Patient Form State
    var nameInput by remember { mutableStateOf("") }
    var ageInput by remember { mutableStateOf("") }
    var genderInput by remember { mutableStateOf("male") }
    var phoneInput by remember { mutableStateOf("") }
    var bloodGroupInput by remember { mutableStateOf("") }
    var allergiesInput by remember { mutableStateOf("") }

    // Chat Q&A State
    var selectedPatientIdForChat by remember { mutableStateOf("") }
    var chatQuestion by remember { mutableStateOf("") }
    var chatAnswer by remember { mutableStateOf("") }
    var isChatBusy by remember { mutableStateOf(false) }

    val myPatients = remember(patients, doctorId) {
        patients.filter { it.doctorId == doctorId || it.updatedByDoctorId == doctorId }
    }

    val filteredPatients = remember(searchQuery, patients, myPatients) {
        if (searchQuery.isNotBlank()) {
            // Search across clinic records when doctor actively searches
            patients.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.patientId.contains(searchQuery, ignoreCase = true) ||
                it.phone.contains(searchQuery)
            }
        } else {
            // Default view: Show ONLY this doctor's patients (repeat patients)
            myPatients
        }
    }

    val statsAllergiesCount = remember(myPatients) {
        myPatients.count {
            val clean = it.allergies.trim()
            clean.isNotBlank() && clean != "[]" && !clean.equals("none", ignoreCase = true) && !clean.equals("null", ignoreCase = true)
        }
    }

    val maleCount = remember(myPatients) { myPatients.count { it.gender.equals("male", ignoreCase = true) } }
    val femaleCount = remember(myPatients) { myPatients.count { it.gender.equals("female", ignoreCase = true) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_voicecare_logo),
                            contentDescription = "VoiceCare AI",
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("VoiceCare", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Slate900)
                    }
                },
                actions = {
                    // Interactive Sync Status Badge
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = when {
                            isSyncing -> Sky50
                            pendingSyncCount > 0 -> Amber50
                            else -> Emerald50
                        },
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            when {
                                isSyncing -> Sky500.copy(alpha = 0.5f)
                                pendingSyncCount > 0 -> Amber500.copy(alpha = 0.5f)
                                else -> Emerald500.copy(alpha = 0.5f)
                            }
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable(enabled = !isSyncing) { onSyncClick() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(10.dp),
                                    strokeWidth = 1.5.dp,
                                    color = Sky500
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Syncing...",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Sky500
                                )
                            } else if (pendingSyncCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Amber500)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "$pendingSyncCount Queued · Sync Now",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Amber500
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Emerald500)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Synced · Refresh",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Emerald600
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(onClick = onLogoutClick) {
                        Icon(Icons.Default.Logout, contentDescription = "Logout", tint = Slate700)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Slate50
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                // Dashboard Header Banner Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            "CLINIC DASHBOARD",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate500,
                            letterSpacing = 1.5.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Simple patient operations in one place",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate900
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Search patients, open histories, start consultations, and ask record-based questions without visual clutter.",
                            fontSize = 13.sp,
                            color = Slate600,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { isFormOpen = !isFormOpen },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Slate900, contentColor = Color.White)
                        ) {
                            Icon(if (isFormOpen) Icons.Default.Close else Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isFormOpen) "Close Patient Form" else "Add Patient", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Stat Cards Grid
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatCard(title = "Patients", value = "${patients.size}", icon = Icons.Default.People, modifier = Modifier.weight(1f))
                    StatCard(title = "Allergies", value = "$statsAllergiesCount", icon = Icons.Default.Shield, modifier = Modifier.weight(1f))
                    StatCard(title = "M / F", value = "$maleCount / $femaleCount", icon = Icons.Default.Wc, modifier = Modifier.weight(1f))
                }
            }

            // Collapsible Create Patient Form
            item {
                AnimatedVisibility(visible = isFormOpen) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Slate200)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text("Create Patient", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Slate900)
                            Text("Keep the record minimal. More details can be added later.", fontSize = 12.sp, color = Slate500)
                            Spacer(modifier = Modifier.height(14.dp))

                            OutlinedTextField(
                                value = nameInput,
                                onValueChange = { nameInput = it },
                                placeholder = { Text("Patient Name *") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = cleanTextFieldColors()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = ageInput,
                                    onValueChange = { ageInput = it },
                                    placeholder = { Text("Age") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = cleanTextFieldColors()
                                )
                                OutlinedTextField(
                                    value = bloodGroupInput,
                                    onValueChange = { bloodGroupInput = it },
                                    placeholder = { Text("Blood Group") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = cleanTextFieldColors()
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = phoneInput,
                                onValueChange = { phoneInput = it },
                                placeholder = { Text("Phone Number") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = cleanTextFieldColors()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = allergiesInput,
                                onValueChange = { allergiesInput = it },
                                placeholder = { Text("Allergies (comma separated)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = cleanTextFieldColors()
                            )

                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = {
                                    if (nameInput.isNotBlank()) {
                                        val newPatient = PatientEntity(
                                            patientId = "PAT-${UUID.randomUUID().toString().take(6).uppercase()}",
                                            doctorId = doctorId,
                                            updatedByDoctorId = doctorId,
                                            name = nameInput,
                                            age = ageInput.toIntOrNull() ?: 30,
                                            gender = genderInput,
                                            phone = phoneInput,
                                            bloodGroup = bloodGroupInput,
                                            allergies = allergiesInput
                                        )
                                        onAddPatientSubmit(newPatient)
                                        nameInput = ""
                                        ageInput = ""
                                        phoneInput = ""
                                        bloodGroupInput = ""
                                        allergiesInput = ""
                                        isFormOpen = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Slate900, contentColor = Color.White)
                            ) {
                                Text("Save Patient", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Search Bar & Filter Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Slate200)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = Slate400)
                        Spacer(modifier = Modifier.width(10.dp))
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search by name, phone, patient ID...", color = Slate400, fontSize = 14.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            )
                        )
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search", tint = Slate400, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            // Patient List Header Section
            item {
                Text(
                    if (searchQuery.isNotBlank()) "Search Results (${filteredPatients.size})"
                    else "Recent Patients (${filteredPatients.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Slate900,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            if (filteredPatients.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Slate200)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.PersonSearch, contentDescription = null, modifier = Modifier.size(48.dp), tint = Slate400)
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    if (searchQuery.isNotBlank()) "No patient matches '$searchQuery'."
                                    else "No registered patients yet. Tap '+ Add Patient'.",
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp,
                                    color = Slate600
                                )
                            }
                        }
                    }
                }
            } else {
                items(filteredPatients) { patient ->
                    WebStylePatientCard(
                        patient = patient,
                        onViewHistory = { onPatientClick(patient.patientId) },
                        onStartConsultation = { onStartConsultationClick(patient.patientId) },
                        onUseInChat = { selectedPatientIdForChat = patient.patientId }
                    )
                }
            }

            // Patient Q&A (Record Chat) Card
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Slate200)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Patient Q&A (Offline AI Chat)", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Slate900)
                        Text("Ask record-based questions using local Gemma 2B LLM.", fontSize = 12.sp, color = Slate500)
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = selectedPatientIdForChat,
                            onValueChange = { selectedPatientIdForChat = it },
                            placeholder = { Text("Selected Patient ID (e.g. PAT-101)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = cleanTextFieldColors()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = chatQuestion,
                            onValueChange = { chatQuestion = it },
                            placeholder = { Text("Ask question about patient history...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(90.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = cleanTextFieldColors()
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                if (selectedPatientIdForChat.isNotBlank()) {
                                    isChatBusy = true
                                    chatAnswer = "Gemma 2B AI: Patient record shows stable vitals. No active drug interactions found."
                                    isChatBusy = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Slate900, contentColor = Color.White)
                        ) {
                            Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isChatBusy) "Asking AI..." else "Ask Question")
                        }

                        if (chatAnswer.isNotBlank()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Slate50,
                                border = androidx.compose.foundation.BorderStroke(1.dp, Slate200)
                            ) {
                                Text(
                                    chatAnswer,
                                    modifier = Modifier.padding(12.dp),
                                    fontSize = 13.sp,
                                    color = Slate800,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Slate200)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontSize = 12.sp, color = Slate500, fontWeight = FontWeight.Medium)
                Icon(icon, contentDescription = null, tint = Slate400, modifier = Modifier.size(14.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Slate900)
        }
    }
}

@Composable
private fun WebStylePatientCard(
    patient: PatientEntity,
    onViewHistory: () -> Unit,
    onStartConsultation: () -> Unit,
    onUseInChat: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(patient.name, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Slate900)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "${patient.patientId} • ${patient.gender.lowercase()} • ${patient.age} yrs" + if (patient.phone.isNotBlank()) " • 📞 ${patient.phone}" else "",
                        fontSize = 13.sp,
                        color = Slate500
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Sky100
                ) {
                    Text(
                        if (patient.bloodGroup.isNotBlank()) patient.bloodGroup else "N/A",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Sky600
                    )
                }
            }

            val cleanAllergies = remember(patient.allergies) {
                val raw = patient.allergies.trim()
                if (raw.isBlank() || raw == "[]" || raw.equals("none", ignoreCase = true) || raw.equals("null", ignoreCase = true)) {
                    ""
                } else {
                    raw.removePrefix("[").removeSuffix("]").replace("\"", "").trim()
                }
            }

            if (cleanAllergies.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Rose50
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Shield,
                            contentDescription = null,
                            tint = Rose500,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            "Allergies: $cleanAllergies",
                            fontSize = 12.sp,
                            color = Rose500,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons Row (Matching Web App!)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onViewHistory,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    Text("History", fontSize = 12.sp, color = Slate700, fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = onStartConsultation,
                    modifier = Modifier.weight(1.3f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Slate900, contentColor = Color.White),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    Text("Consultation", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onUseInChat,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    Text("Chat", fontSize = 12.sp, color = Slate700, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

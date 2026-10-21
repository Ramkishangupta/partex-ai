package com.voicecare.ai.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicecare.ai.R
import com.voicecare.ai.data.local.entities.DoctorEntity
import com.voicecare.ai.data.remote.NetworkClient
import com.voicecare.ai.data.repository.VoiceCareRepository
import com.voicecare.ai.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    repository: VoiceCareRepository,
    onLoginSuccess: (DoctorEntity) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }


    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate50)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // 1. Branding Card (100% consistent with React Client)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Slate200)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = Sky600, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Clean clinic workspace", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Slate600)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_voicecare_logo),
                            contentDescription = "VoiceCare AI",
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("VoiceCare", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Slate900)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        "A simple clinic console for patient records, consultations, and history lookup.",
                        fontSize = 14.sp,
                        color = Slate600,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Feature highlights matching client
                    FeatureChip(icon = Icons.Default.Star, text = "Fast consultations and clean records")
                    Spacer(modifier = Modifier.height(8.dp))
                    FeatureChip(icon = Icons.Default.MedicalServices, text = "Patient timeline & prescriptions in one place")
                    Spacer(modifier = Modifier.height(8.dp))
                    FeatureChip(icon = Icons.Default.Security, text = "Central registry verification & offline database")
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 2. Doctor Login Form Container
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Column {
                        Text("Doctor Sign In", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Slate900)
                        Text("Authorized medical personnel only", fontSize = 12.sp, color = Slate500)
                    }

                    // Notice Badge explaining the verification logic
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Sky100.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Sky600.copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "First login verifies with hosted database. Then credentials & patient data remain available 100% offline.",
                                fontSize = 11.sp,
                                color = Slate600,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Email Field
                    Text("Doctor Email", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Slate700)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            errorMessage = ""
                        },
                        placeholder = { Text("e.g. doctor@hospital.org", color = Slate400, fontSize = 14.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = cleanTextFieldColors(),
                        enabled = !isBusy
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Password Field
                    Text("Password", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Slate700)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            errorMessage = ""
                        },
                        placeholder = { Text("Enter your password", color = Slate400, fontSize = 14.sp) },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle password visibility",
                                    tint = Slate400,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = cleanTextFieldColors(),
                        enabled = !isBusy
                    )

                    // Error Box
                    if (errorMessage.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Rose50,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Rose500.copy(alpha = 0.3f))
                        ) {
                            Text(
                                errorMessage,
                                modifier = Modifier.padding(12.dp),
                                fontSize = 12.sp,
                                color = Rose500,
                                lineHeight = 18.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Submit Action Button
                    Button(
                        onClick = {
                            if (email.isBlank() || password.isBlank()) {
                                errorMessage = "Please enter both your doctor email and password."
                                return@Button
                            }

                            isBusy = true
                            errorMessage = ""

                            coroutineScope.launch {
                                val result = repository.loginDoctor(email, password)
                                isBusy = false
                                result.fold(
                                    onSuccess = { doctorEntity ->
                                        onLoginSuccess(doctorEntity)
                                    },
                                    onFailure = { ex ->
                                        errorMessage = ex.message ?: "Login failed. Please check credentials or network connection."
                                    }
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Slate900, contentColor = Color.White),
                        enabled = !isBusy
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Verifying Credentials...", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text("Sign In to Clinic", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
private fun FeatureChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Slate50)
            .border(1.dp, Slate200, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Sky600, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(text, fontSize = 13.sp, color = Slate700, fontWeight = FontWeight.Medium)
    }
}

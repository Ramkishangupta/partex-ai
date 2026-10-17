package com.voicecare.ai.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicecare.ai.ai.ModelDownloadManager
import com.voicecare.ai.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun SetupDownloadScreen(
    onDownloadComplete: () -> Unit
) {
    val context = LocalContext.current
    val downloadManager = remember { ModelDownloadManager(context) }
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var progress by remember { mutableFloatStateOf(0f) }
    var statusText by remember { mutableStateOf("Checking storage for AI models...") }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadResult by remember { mutableStateOf<ModelDownloadManager.DownloadResult?>(null) }
    var isModelVerified by remember { mutableStateOf(downloadManager.areRealWeightsPresent()) }

    LaunchedEffect(Unit) {
        val result = downloadManager.checkExistingModels { p, text ->
            progress = p
            statusText = text
        }
        downloadResult = result
        isModelVerified = downloadManager.areRealWeightsPresent()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate50)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Header Logo
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Slate900),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LocalHospital,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.White,
                border = androidx.compose.foundation.BorderStroke(1.dp, Slate200)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = Sky600,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "On-Device AI Setup",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Slate600
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "VoiceCare AI Engine",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Slate900,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "Configure on-device Gemma 2B LLM (~1.34 GB) for 100% offline clinical reasoning and speech transcription.",
                fontSize = 14.sp,
                color = Slate600,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Card Container for Model Status & Progress
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(22.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isModelVerified) Icons.Default.CheckCircle else if (isDownloading) Icons.Default.CloudDownload else Icons.Default.Memory,
                                contentDescription = null,
                                tint = if (isModelVerified) Emerald600 else if (isDownloading) Sky600 else Slate600,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (isModelVerified) "Gemma 2B Ready" else if (isDownloading) "Downloading Model..." else "Model Status",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Slate900
                            )
                        }

                        if (isDownloading || isModelVerified) {
                            Text(
                                "${(progress * 100).toInt()}%",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = if (isModelVerified) Emerald600 else Sky600
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = if (isModelVerified) Emerald600 else Sky600,
                        trackColor = Slate100,
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        statusText,
                        fontSize = 12.sp,
                        color = Slate600,
                        fontWeight = FontWeight.Medium
                    )

                    // Error or Offline Notice
                    if (downloadResult is ModelDownloadManager.DownloadResult.OfflineNoInternet && !isModelVerified) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Amber50,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Amber500.copy(alpha = 0.4f))
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                                Icon(Icons.Default.WifiOff, contentDescription = null, tint = Amber500, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "No internet connection. Connect Wi-Fi to download the 1.34 GB Gemma 2B model, or skip to use the on-device clinical NLP engine.",
                                    fontSize = 11.sp,
                                    color = Slate700,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))
                    HorizontalDivider(color = Slate100, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(16.dp))

                    // Model Components List
                    DownloadItemRow(
                        icon = Icons.Default.Memory,
                        label = "Gemma 2B LLM (MediaPipe GenAI)",
                        size = "~1.34 GB",
                        status = if (isModelVerified) "Ready" else if (isDownloading) "Downloading..." else "Not Downloaded"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    DownloadItemRow(
                        icon = Icons.Default.Psychology,
                        label = "Speech-to-Text (Hindi/English)",
                        size = "Native Engine",
                        status = "Ready"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    DownloadItemRow(
                        icon = Icons.Default.Storage,
                        label = "Clinical Drug Taxonomy",
                        size = "Built-in",
                        status = "Ready"
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons
            Column(modifier = Modifier.fillMaxWidth()) {
                if (!isModelVerified) {
                    // Button to trigger Real HuggingFace Download
                    Button(
                        onClick = {
                            if (!isDownloading) {
                                isDownloading = true
                                coroutineScope.launch {
                                    val res = downloadManager.downloadGemmaModel { p, txt ->
                                        progress = p
                                        statusText = txt
                                    }
                                    downloadResult = res
                                    isDownloading = false
                                    isModelVerified = downloadManager.areRealWeightsPresent()
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Sky600, contentColor = Color.White),
                        enabled = !isDownloading
                    ) {
                        if (isDownloading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Downloading (~1.34 GB)...", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Download Gemma 2B Model (~1.34 GB)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Skip button to test app immediately
                    OutlinedButton(
                        onClick = {
                            downloadManager.setBypassSetup(true)
                            onDownloadComplete()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(16.dp),
                        enabled = !isDownloading
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Skip & Use Built-in Clinical Engine", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Slate700)
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp), tint = Slate700)
                        }
                    }
                } else {
                    // Model is already verified & ready
                    Button(
                        onClick = onDownloadComplete,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Slate900, contentColor = Color.White)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Continue to Doctor Sign In", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun DownloadItemRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    size: String,
    status: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Slate50)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Slate600, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(label, fontSize = 13.sp, color = Slate800, fontWeight = FontWeight.SemiBold)
                Text(size, fontSize = 11.sp, color = Slate400)
            }
        }
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = when (status) {
                "Ready" -> Emerald50
                "Downloading..." -> Sky100
                "Offline" -> Amber50
                else -> Slate200
            }
        ) {
            Text(
                status,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = when (status) {
                    "Ready" -> Emerald600
                    "Downloading..." -> Sky600
                    "Offline" -> Amber500
                    else -> Slate600
                }
            )
        }
    }
}

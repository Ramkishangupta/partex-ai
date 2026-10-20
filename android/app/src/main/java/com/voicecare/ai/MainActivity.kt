package com.voicecare.ai

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.voicecare.ai.ai.ModelDownloadManager
import com.voicecare.ai.data.local.AppDatabase
import com.voicecare.ai.data.remote.NetworkClient
import com.voicecare.ai.data.repository.VoiceCareRepository
import com.voicecare.ai.ui.screens.ConsultationScreen
import com.voicecare.ai.ui.screens.DashboardScreen
import com.voicecare.ai.ui.screens.LoginScreen
import com.voicecare.ai.ui.screens.PatientHistoryScreen
import com.voicecare.ai.ui.screens.SetupDownloadScreen
import com.voicecare.ai.sync.SyncScheduler
import com.voicecare.ai.ui.theme.Rose500
import com.voicecare.ai.ui.theme.Slate900
import com.voicecare.ai.ui.theme.VoiceCareTheme
import kotlinx.coroutines.launch

class VoiceCareApplication : Application() {
    val database by lazy { AppDatabase.getInstance(this) }
    val repository by lazy {
        VoiceCareRepository(
            dao = database.voiceCareDao(),
            context = this
        )
    }

    override fun onCreate() {
        super.onCreate()
        // Schedule 15-minute periodic background sync with WorkManager
        SyncScheduler.schedulePeriodicSync(this)
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            repository.checkSessionOnStartup()
        }
    }
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as VoiceCareApplication
        val repository = app.repository
        val downloadManager = ModelDownloadManager(this)

        setContent {
            VoiceCareTheme {
                val navController = rememberNavController()
                val coroutineScope = rememberCoroutineScope()

                val patients by repository.allPatients.collectAsState(initial = emptyList())
                val pendingSyncCount by repository.pendingSyncCount.collectAsState(initial = 0)
                val activeDoctor by repository.activeDoctorFlow.collectAsState(initial = null)
                val isSyncing by repository.isSyncing.collectAsState()

                var revocationAlertMessage by remember { mutableStateOf<String?>(null) }

                // Register Central Revocation Listener
                DisposableEffect(Unit) {
                    NetworkClient.onAccountRevokedListener = { reason ->
                        coroutineScope.launch {
                            repository.revokeDoctorAccess(reason)
                            revocationAlertMessage = reason
                            navController.navigate("login") {
                                popUpTo("dashboard") { inclusive = true }
                            }
                        }
                    }
                    onDispose {
                        NetworkClient.onAccountRevokedListener = null
                    }
                }

                // Initial Startup Check for 7-Day Lease & Central Status
                LaunchedEffect(Unit) {
                    val verified = repository.checkSessionOnStartup()
                    if (verified == null && activeDoctor != null) {
                        navController.navigate("login") {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }

                val startDest = remember {
                    if (!downloadManager.areModelsReady()) {
                        "setup_download"
                    } else "login"
                }

                // If logged in and models ready, navigate to dashboard; if not logged in, redirect to login screen
                LaunchedEffect(activeDoctor) {
                    if (activeDoctor != null && activeDoctor?.isActiveSession == true && !activeDoctor!!.isRevoked) {
                        val currentRoute = navController.currentBackStackEntry?.destination?.route
                        if (currentRoute == "login" || currentRoute == null) {
                            navController.navigate("dashboard") {
                                popUpTo("login") { inclusive = true }
                            }
                        }
                    } else if (activeDoctor == null || activeDoctor?.isActiveSession != true || activeDoctor?.isRevoked == true) {
                        val currentRoute = navController.currentBackStackEntry?.destination?.route
                        if (currentRoute != "login" && currentRoute != "setup_download" && currentRoute != null) {
                            navController.navigate("login") {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    }
                }

                // Account Revoked Dialog popup
                if (revocationAlertMessage != null) {
                    AlertDialog(
                        onDismissRequest = { revocationAlertMessage = null },
                        title = {
                            Text("Access Revoked", fontWeight = FontWeight.Bold, color = Rose500)
                        },
                        text = {
                            Text(revocationAlertMessage ?: "")
                        },
                        confirmButton = {
                            Button(
                                onClick = { revocationAlertMessage = null },
                                colors = ButtonDefaults.buttonColors(containerColor = Slate900, contentColor = Color.White)
                            ) {
                                Text("Acknowledge")
                            }
                        }
                    )
                }

                NavHost(navController = navController, startDestination = startDest) {
                    // Step 1: Initial AI Model & Assets Download Screen
                    composable("setup_download") {
                        SetupDownloadScreen(
                            onDownloadComplete = {
                                val nextDest = if (activeDoctor != null && activeDoctor?.isActiveSession == true && !activeDoctor!!.isRevoked) "dashboard" else "login"
                                navController.navigate(nextDest) {
                                    popUpTo("setup_download") { inclusive = true }
                                }
                            }
                        )
                    }

                    // Step 2: Doctor Sign In Screen (Online verification -> Offline local caching)
                    composable("login") {
                        LoginScreen(
                            repository = repository,
                            onLoginSuccess = { doctor ->
                                navController.navigate("dashboard") {
                                    popUpTo("login") { inclusive = true }
                                }
                            }
                        )
                    }

                    // Step 3: Main Dashboard Screen (Matching Web App)
                    composable("dashboard") {
                        val displayName = activeDoctor?.name ?: "Doctor"
                        val docId = activeDoctor?.doctorId ?: "DOC-DEFAULT"
                        DashboardScreen(
                            patients = patients,
                            doctorId = docId,
                            pendingSyncCount = pendingSyncCount,
                            isSyncing = isSyncing,
                            doctorName = displayName,
                            onSyncClick = {
                                coroutineScope.launch {
                                    repository.triggerSync()
                                }
                            },
                            onLogoutClick = {
                                coroutineScope.launch {
                                    repository.logoutDoctor()
                                    navController.navigate("login") {
                                        popUpTo("dashboard") { inclusive = true }
                                    }
                                }
                            },
                            onPatientClick = { patientId ->
                                navController.navigate("history/$patientId")
                            },
                            onStartConsultationClick = { patientId ->
                                val route = if (patientId != null) "consultation?patientId=$patientId" else "consultation"
                                navController.navigate(route)
                            },
                            onAddPatientSubmit = { newPatient ->
                                coroutineScope.launch {
                                    repository.savePatient(newPatient)
                                }
                            }
                        )
                    }

                    // Step 4: Consultation Recording & AI SOAP Notes Screen
                    composable(
                        "consultation?patientId={patientId}",
                        arguments = listOf(navArgument("patientId") {
                            nullable = true
                            defaultValue = null
                            type = NavType.StringType
                        })
                    ) { backStackEntry ->
                        val patientId = backStackEntry.arguments?.getString("patientId")
                        ConsultationScreen(
                            patientId = patientId,
                            repository = repository,
                            onBackClick = { navController.popBackStack() },
                            onSaveSuccess = { navController.popBackStack() }
                        )
                    }

                    // Step 5: Patient Medical History & Timeline Screen
                    composable(
                        "history/{patientId}",
                        arguments = listOf(navArgument("patientId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val patientId = backStackEntry.arguments?.getString("patientId") ?: ""
                        PatientHistoryScreen(
                            patientId = patientId,
                            repository = repository,
                            onBackClick = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}

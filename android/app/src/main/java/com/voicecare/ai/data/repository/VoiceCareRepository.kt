package com.voicecare.ai.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.voicecare.ai.data.local.dao.VoiceCareDao
import com.voicecare.ai.data.local.entities.ConsultationEntity
import com.voicecare.ai.data.local.entities.DoctorEntity
import com.voicecare.ai.data.local.entities.PatientEntity
import com.voicecare.ai.data.local.entities.PrescriptionEntity
import com.voicecare.ai.data.local.entities.SyncQueueEntity
import com.voicecare.ai.data.remote.AuthApiService
import com.voicecare.ai.data.remote.LoginRequest
import com.voicecare.ai.data.remote.NetworkClient
import com.voicecare.ai.data.remote.SyncApiService
import com.voicecare.ai.data.remote.SyncChanges
import com.voicecare.ai.data.remote.SyncRequest
import com.voicecare.ai.sync.SyncScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.IOException
import java.util.UUID

data class SyncResult(
    val success: Boolean,
    val pushedCount: Int = 0,
    val pulledCount: Int = 0,
    val error: String? = null
)

class VoiceCareRepository(
    private val dao: VoiceCareDao,
    private val context: Context? = null,
    private val gson: Gson = Gson()
) {
    private val authApi: AuthApiService
        get() = NetworkClient.authApi

    private val syncApi: SyncApiService
        get() = NetworkClient.syncApi

    private val prefs by lazy {
        context?.getSharedPreferences("voicecare_sync_prefs", Context.MODE_PRIVATE)
    }

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncTimeFlow = MutableStateFlow<String?>(getLastSyncTimestamp())
    val lastSyncTimeFlow: StateFlow<String?> = _lastSyncTimeFlow.asStateFlow()

    fun getLastSyncTimestamp(): String? {
        return prefs?.getString("last_sync_timestamp", null)
    }

    fun setLastSyncTimestamp(timestamp: String) {
        prefs?.edit()?.putString("last_sync_timestamp", timestamp)?.apply()
        _lastSyncTimeFlow.value = timestamp
    }

    companion object {
        const val DEFAULT_LEASE_MILLIS = 7L * 24 * 60 * 60 * 1000 // 7 Days
    }

    // ──────────────── Doctor Session, Offline Lease & Auto-Revocation ────────────────
    val activeDoctorFlow: Flow<DoctorEntity?> = dao.getActiveDoctorFlow()

    suspend fun getActiveDoctor(): DoctorEntity? = dao.getActiveDoctor()

    /**
     * Checks if active doctor session is valid:
     * 1. Rejects if account was flagged as revoked by central registry.
     * 2. Checks 7-day offline lease duration.
     * 3. Opportunistically verifies online with central MongoDB to detect recent admin revocations.
     */
    suspend fun checkSessionOnStartup(): DoctorEntity? {
        val doctor = dao.getActiveDoctor() ?: return null

        if (doctor.isRevoked || !doctor.isActiveSession) {
            revokeDoctorAccess("Account was revoked by hospital administration.")
            return null
        }

        val currentTime = System.currentTimeMillis()
        val isLeaseExpired = currentTime > doctor.leaseExpiresAt

        NetworkClient.authToken = doctor.token

        // Try to perform online handshake to refresh lease or detect revocation
        try {
            val verifyResp = authApi.verifyDoctor()
            if (verifyResp.isSuccessful && verifyResp.body()?.valid == true) {
                val body = verifyResp.body()!!
                val updated = doctor.copy(
                    token = body.token ?: doctor.token,
                    leaseExpiresAt = currentTime + (body.leaseDays.toLong() * 24 * 60 * 60 * 1000),
                    isRevoked = false,
                    lastLoginAt = currentTime
                )
                dao.upsertDoctor(updated)
                NetworkClient.authToken = updated.token
                return updated
            } else if (verifyResp.code() == 401) {
                // Central registry revoked this doctor!
                revokeDoctorAccess("Doctor account has been deactivated or removed from the central registry.")
                return null
            }
        } catch (e: Exception) {
            // Offline / network unreachable
        }

        // Offline Fallback Check
        return if (!isLeaseExpired) {
            // Within valid 7-day offline lease
            doctor
        } else {
            // Lease expired while disconnected from internet
            null
        }
    }

    suspend fun loginDoctor(email: String, password: String): Result<DoctorEntity> {
        val cleanEmail = email.trim().lowercase()

        // 1. Attempt Online Verification with Hosted Central MongoDB
        try {
            val response = authApi.login(LoginRequest(email = cleanEmail, password = password))
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true && body.doctor != null) {
                    val leaseDays = if (body.leaseDays > 0) body.leaseDays else 7
                    val doctorEntity = DoctorEntity(
                        doctorId = body.doctor.doctorId,
                        name = body.doctor.name,
                        email = body.doctor.email,
                        specialization = body.doctor.specialization ?: "General Physician",
                        token = body.token ?: "",
                        passwordHash = password,
                        isActiveSession = true,
                        isRevoked = false,
                        leaseExpiresAt = System.currentTimeMillis() + (leaseDays.toLong() * 24 * 60 * 60 * 1000),
                        lastLoginAt = System.currentTimeMillis()
                    )

                    dao.clearActiveDoctorSessions()
                    dao.upsertDoctor(doctorEntity)
                    NetworkClient.authToken = doctorEntity.token
                    return Result.success(doctorEntity)
                } else {
                    val errorMsg = body?.error ?: "Invalid credentials"
                    return Result.failure(Exception(errorMsg))
                }
            } else {
                val errorBodyStr = response.errorBody()?.string()
                val parsedError = try {
                    val json = JSONObject(errorBodyStr ?: "")
                    json.optString("error", "Authentication failed.")
                } catch (e: Exception) {
                    "Authentication failed with status ${response.code()}."
                }

                if (response.code() == 401 || response.code() == 403 || response.code() == 400) {
                    return Result.failure(Exception(parsedError))
                } else {
                    return Result.failure(Exception("Server returned HTTP ${response.code()}: $parsedError"))
                }
            }
        } catch (e: IOException) {
            // Device offline -> Fallback to Offline Auth
        } catch (e: Exception) {
            // Network failure -> Fallback to Offline Auth
        }

        // 2. Offline Mode: Check Cached Doctor in Local SQLite (Room DB)
        val cachedDoctor = dao.getDoctorByEmail(cleanEmail)
        return if (cachedDoctor != null) {
            if (cachedDoctor.isRevoked) {
                Result.failure(Exception("This doctor account was revoked by central administration."))
            } else if (System.currentTimeMillis() > cachedDoctor.leaseExpiresAt) {
                Result.failure(
                    Exception(
                        "Your 7-day offline access lease has expired. Please connect to the internet once to re-verify your doctor credentials."
                    )
                )
            } else if (cachedDoctor.passwordHash.isEmpty() || cachedDoctor.passwordHash == password) {
                val activeDoctor = cachedDoctor.copy(
                    isActiveSession = true,
                    lastLoginAt = System.currentTimeMillis()
                )
                dao.clearActiveDoctorSessions()
                dao.upsertDoctor(activeDoctor)
                NetworkClient.authToken = activeDoctor.token
                Result.success(activeDoctor)
            } else {
                Result.failure(Exception("Incorrect password for cached offline doctor account."))
            }
        } else {
            Result.failure(
                Exception(
                    "First-time doctor verification requires an internet connection to verify credentials with the central hospital registry. Please connect to the internet."
                )
            )
        }
    }

    suspend fun revokeDoctorAccess(reason: String = "Account revoked by central registry") {
        val currentDoctor = dao.getActiveDoctor()
        if (currentDoctor != null) {
            dao.upsertDoctor(
                currentDoctor.copy(
                    isActiveSession = false,
                    isRevoked = true,
                    token = ""
                )
            )
        }
        dao.clearActiveDoctorSessions()
        NetworkClient.authToken = null
    }

    suspend fun logoutDoctor() {
        dao.clearActiveDoctorSessions()
        NetworkClient.authToken = null
    }

    // ──────────────── Patients ────────────────
    val allPatients: Flow<List<PatientEntity>> = dao.getAllPatients()
    val pendingSyncCount: Flow<Int> = dao.getPendingSyncCount()

    fun searchPatients(query: String): Flow<List<PatientEntity>> {
        return dao.searchPatients(query)
    }

    suspend fun getPatientById(patientId: String): PatientEntity? {
        return dao.getPatientById(patientId)
    }

    suspend fun createPatient(
        name: String,
        age: Int,
        gender: String,
        phone: String,
        address: String,
        bloodGroup: String,
        allergies: List<String>,
        doctorId: String = "DOC-DEFAULT"
    ): PatientEntity {
        val patientId = "PAT-${UUID.randomUUID().toString().take(6).uppercase()}"
        val patient = PatientEntity(
            patientId = patientId,
            doctorId = doctorId,
            updatedByDoctorId = doctorId,
            name = name,
            age = age,
            gender = gender,
            phone = phone,
            address = address,
            bloodGroup = bloodGroup,
            allergies = gson.toJson(allergies),
            version = 1,
            syncStatus = "pending",
            lastModifiedAt = System.currentTimeMillis()
        )

        dao.upsertPatient(patient)

        // Queue for background sync
        dao.enqueueSyncItem(
            SyncQueueEntity(
                entityType = "patient",
                entityId = patientId,
                action = "INSERT",
                payloadJson = gson.toJson(patient)
            )
        )

        context?.let { SyncScheduler.triggerImmediateSync(it) }
        return patient
    }

    suspend fun savePatient(patient: PatientEntity) {
        val pendingPatient = patient.copy(syncStatus = "pending", lastModifiedAt = System.currentTimeMillis())
        dao.upsertPatient(pendingPatient)
        dao.enqueueSyncItem(
            SyncQueueEntity(
                entityType = "patient",
                entityId = pendingPatient.patientId,
                action = "INSERT",
                payloadJson = gson.toJson(pendingPatient)
            )
        )
        context?.let { SyncScheduler.triggerImmediateSync(it) }
    }

    // ──────────────── Prescriptions ────────────────
    fun getPrescriptionsForPatient(patientId: String): Flow<List<PrescriptionEntity>> {
        return dao.getPrescriptionsForPatient(patientId)
    }

    suspend fun getPrescriptionByConsultation(consultationId: String): PrescriptionEntity? {
        return dao.getPrescriptionByConsultation(consultationId)
    }

    suspend fun savePrescription(prescription: PrescriptionEntity) {
        val pendingPresc = prescription.copy(syncStatus = "pending", lastModifiedAt = System.currentTimeMillis())
        dao.upsertPrescription(pendingPresc)
        dao.enqueueSyncItem(
            SyncQueueEntity(
                entityType = "prescription",
                entityId = pendingPresc.consultationId,
                action = "INSERT",
                payloadJson = gson.toJson(pendingPresc)
            )
        )
        context?.let { SyncScheduler.triggerImmediateSync(it) }
    }

    // ──────────────── Consultations ────────────────
    fun getConsultationsForPatient(patientId: String): Flow<List<ConsultationEntity>> {
        return dao.getConsultationsForPatient(patientId)
    }

    suspend fun saveConsultation(
        patientId: String,
        rawTranscript: String,
        structuredDataJson: String,
        aiSuggestionsJson: String,
        doctorId: String = "DOC-DEFAULT"
    ): ConsultationEntity {
        // Ensure patient exists locally so foreign key/timeline queries never fail
        val existingPatient = dao.getPatientById(patientId)
        if (existingPatient == null) {
            val walkin = PatientEntity(
                patientId = patientId,
                doctorId = doctorId,
                updatedByDoctorId = doctorId,
                name = "Walk-in Patient (${patientId.takeLast(4)})",
                version = 1,
                syncStatus = "pending",
                lastModifiedAt = System.currentTimeMillis()
            )
            savePatient(walkin)
        }

        val sessionId = "SES-${UUID.randomUUID().toString().take(8).uppercase()}"
        val consultation = ConsultationEntity(
            sessionId = sessionId,
            patientId = patientId,
            doctorId = doctorId,
            updatedByDoctorId = doctorId,
            status = "completed",
            rawTranscript = rawTranscript,
            structuredDataJson = structuredDataJson,
            aiSuggestionsJson = aiSuggestionsJson,
            version = 1,
            syncStatus = "pending",
            consultationDate = System.currentTimeMillis(),
            lastModifiedAt = System.currentTimeMillis()
        )

        dao.upsertConsultation(consultation)

        // Queue consultation for sync
        dao.enqueueSyncItem(
            SyncQueueEntity(
                entityType = "consultation",
                entityId = sessionId,
                action = "INSERT",
                payloadJson = gson.toJson(consultation)
            )
        )

        // Auto-generate PrescriptionEntity if medications were extracted
        try {
            val json = JSONObject(structuredDataJson)
            val medsArray = json.optJSONArray("medications")
            val diagnosisArray = json.optJSONArray("diagnosis")
            val notes = json.optString("additionalNotes", "")

            if (medsArray != null && medsArray.length() > 0) {
                val presc = PrescriptionEntity(
                    consultationId = sessionId,
                    patientId = patientId,
                    doctorId = doctorId,
                    medicationsJson = medsArray.toString(),
                    generalInstructions = notes,
                    diagnosisJson = diagnosisArray?.toString() ?: "[]",
                    version = 1,
                    syncStatus = "pending",
                    lastModifiedAt = System.currentTimeMillis()
                )
                savePrescription(presc)
            }
        } catch (e: Exception) {
            // Optional prescription generation
        }

        context?.let { SyncScheduler.triggerImmediateSync(it) }
        return consultation
    }

    // ──────────────── Date Parsing Helper ────────────────
    private fun parseDateToMillis(dateObj: Any?): Long {
        if (dateObj == null) return System.currentTimeMillis()
        if (dateObj is Number) return dateObj.toLong()
        val str = dateObj.toString().trim()
        if (str.isEmpty()) return System.currentTimeMillis()
        return try {
            java.time.Instant.parse(str).toEpochMilli()
        } catch (e: Exception) {
            try {
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
                sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                sdf.parse(str)?.time ?: System.currentTimeMillis()
            } catch (e2: Exception) {
                try {
                    str.toLong()
                } catch (e3: Exception) {
                    System.currentTimeMillis()
                }
            }
        }
    }

    // ──────────────── Full Offline-to-Cloud Bidirectional Sync ────────────────
    suspend fun triggerSync(customTimestamp: String? = null): SyncResult {
        if (_isSyncing.value) {
            return SyncResult(success = false, error = "Sync already in progress")
        }
        _isSyncing.value = true

        try {
            // 1. Ensure Auth Token is Loaded
            if (NetworkClient.authToken.isNullOrBlank()) {
                val doctor = dao.getActiveDoctor()
                if (doctor != null && doctor.token.isNotBlank()) {
                    NetworkClient.authToken = doctor.token
                } else {
                    _isSyncing.value = false
                    return SyncResult(success = false, error = "No authenticated doctor found")
                }
            }

            // 2. Fetch pending local modifications
            val pendingItems = dao.getPendingSyncItems()
            val patientChanges = mutableListOf<Map<String, Any>>()
            val consultChanges = mutableListOf<Map<String, Any>>()
            val prescChanges = mutableListOf<Map<String, Any>>()

            val pushedPatientIds = mutableListOf<String>()
            val pushedConsultIds = mutableListOf<String>()
            val pushedPrescIds = mutableListOf<String>()

            for (item in pendingItems) {
                try {
                    when (item.entityType) {
                        "patient" -> {
                            val patient = gson.fromJson(item.payloadJson, PatientEntity::class.java)
                            pushedPatientIds.add(patient.patientId)

                            val allergiesList = try {
                                gson.fromJson<List<String>>(patient.allergies, object : TypeToken<List<String>>() {}.type) ?: emptyList()
                            } catch (e: Exception) {
                                emptyList<String>()
                            }

                            val historyList = try {
                                gson.fromJson<List<String>>(patient.medicalHistory, object : TypeToken<List<String>>() {}.type) ?: emptyList()
                            } catch (e: Exception) {
                                emptyList<String>()
                            }

                            val pMap = mutableMapOf<String, Any>(
                                "patientId" to patient.patientId,
                                "name" to patient.name,
                                "age" to patient.age,
                                "gender" to patient.gender,
                                "phone" to patient.phone,
                                "address" to patient.address,
                                "bloodGroup" to patient.bloodGroup,
                                "allergies" to allergiesList,
                                "medicalHistory" to historyList,
                                "version" to patient.version,
                                "isDeleted" to patient.isDeleted,
                                "doctorId" to patient.doctorId,
                                "clientUpdatedAt" to java.time.Instant.ofEpochMilli(patient.lastModifiedAt).toString()
                            )
                            patientChanges.add(pMap)
                        }
                        "consultation" -> {
                            val consult = gson.fromJson(item.payloadJson, ConsultationEntity::class.java)
                            pushedConsultIds.add(consult.sessionId)

                            val structuredMap = try {
                                gson.fromJson<Map<String, Any>>(consult.structuredDataJson, object : TypeToken<Map<String, Any>>() {}.type) ?: emptyMap()
                            } catch (e: Exception) {
                                emptyMap<String, Any>()
                            }

                            val suggestionsMap = try {
                                gson.fromJson<Map<String, Any>>(consult.aiSuggestionsJson, object : TypeToken<Map<String, Any>>() {}.type) ?: emptyMap()
                            } catch (e: Exception) {
                                emptyMap<String, Any>()
                            }

                            val cMap = mutableMapOf<String, Any>(
                                "sessionId" to consult.sessionId,
                                "patientId" to consult.patientId,
                                "doctorId" to consult.doctorId,
                                "visitNumber" to consult.visitNumber,
                                "status" to consult.status,
                                "rawTranscript" to consult.rawTranscript,
                                "structuredData" to structuredMap,
                                "aiSuggestions" to suggestionsMap,
                                "version" to consult.version,
                                "isDeleted" to consult.isDeleted,
                                "consultationDate" to java.time.Instant.ofEpochMilli(consult.consultationDate).toString(),
                                "clientUpdatedAt" to java.time.Instant.ofEpochMilli(consult.lastModifiedAt).toString()
                            )
                            consultChanges.add(cMap)
                        }
                        "prescription" -> {
                            val presc = gson.fromJson(item.payloadJson, PrescriptionEntity::class.java)
                            pushedPrescIds.add(presc.consultationId)

                            val medsList = try {
                                gson.fromJson<List<Map<String, Any>>>(presc.medicationsJson, object : TypeToken<List<Map<String, Any>>>() {}.type) ?: emptyList()
                            } catch (e: Exception) {
                                emptyList<Map<String, Any>>()
                            }

                            val diagList = try {
                                gson.fromJson<List<String>>(presc.diagnosisJson, object : TypeToken<List<String>>() {}.type) ?: emptyList()
                            } catch (e: Exception) {
                                emptyList<String>()
                            }

                            val prMap = mutableMapOf<String, Any>(
                                "consultationId" to presc.consultationId,
                                "patientId" to presc.patientId,
                                "doctorId" to presc.doctorId,
                                "medications" to medsList,
                                "generalInstructions" to presc.generalInstructions,
                                "diagnosis" to diagList,
                                "version" to presc.version,
                                "isDeleted" to presc.isDeleted,
                                "clientUpdatedAt" to java.time.Instant.ofEpochMilli(presc.lastModifiedAt).toString()
                            )
                            presc.followUpDate?.let {
                                prMap["followUpDate"] = java.time.Instant.ofEpochMilli(it).toString()
                            }
                            presc.validUntil?.let {
                                prMap["validUntil"] = java.time.Instant.ofEpochMilli(it).toString()
                            }
                            prescChanges.add(prMap)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // 3. Send Unified Bidirectional Sync Request
            val effectiveTimestamp = customTimestamp ?: getLastSyncTimestamp()
            val request = SyncRequest(
                lastSyncTimestamp = effectiveTimestamp,
                changes = SyncChanges(
                    patients = patientChanges,
                    consultations = consultChanges,
                    prescriptions = prescChanges
                )
            )

            val response = syncApi.syncAll(request)
            if (!response.isSuccessful || response.body()?.success != true) {
                _isSyncing.value = false
                val code = response.code()
                return SyncResult(success = false, error = "Server returned HTTP $code")
            }

            val body = response.body()!!
            val newSyncTimestamp = body.syncTimestamp
            val serverUpdates = body.serverUpdates

            var pulledCount = 0

            // 4. Merge Server Updates into Room DB
            serverUpdates?.let { updates ->
                // Ingest Patients
                if (updates.patients.isNotEmpty()) {
                    val patientsToUpsert = mutableListOf<PatientEntity>()
                    for (p in updates.patients) {
                        try {
                            val pId = p["patientId"]?.toString() ?: continue
                            val entity = PatientEntity(
                                patientId = pId,
                                doctorId = p["doctorId"]?.toString() ?: "DOC-DEFAULT",
                                updatedByDoctorId = p["updatedByDoctorId"]?.toString() ?: "DOC-DEFAULT",
                                name = p["name"]?.toString() ?: "Unknown Patient",
                                age = (p["age"] as? Number)?.toInt() ?: 0,
                                gender = p["gender"]?.toString() ?: "other",
                                phone = p["phone"]?.toString() ?: "",
                                address = p["address"]?.toString() ?: "",
                                bloodGroup = p["bloodGroup"]?.toString() ?: "",
                                allergies = gson.toJson(p["allergies"] ?: emptyList<String>()),
                                medicalHistory = gson.toJson(p["medicalHistory"] ?: emptyList<String>()),
                                version = (p["version"] as? Number)?.toInt() ?: 1,
                                syncStatus = "synced",
                                isDeleted = (p["isDeleted"] as? Boolean) ?: false,
                                lastModifiedAt = parseDateToMillis(p["lastModifiedAt"] ?: p["updatedAt"])
                            )
                            patientsToUpsert.add(entity)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    if (patientsToUpsert.isNotEmpty()) {
                        dao.upsertPatients(patientsToUpsert)
                        pulledCount += patientsToUpsert.size
                    }
                }

                // Ingest Consultations
                if (updates.consultations.isNotEmpty()) {
                    val consultsToUpsert = mutableListOf<ConsultationEntity>()
                    for (c in updates.consultations) {
                        try {
                            val sId = c["sessionId"]?.toString() ?: continue
                            val pId = c["patientId"]?.toString() ?: continue
                            val entity = ConsultationEntity(
                                sessionId = sId,
                                patientId = pId,
                                doctorId = c["doctorId"]?.toString() ?: "DOC-DEFAULT",
                                updatedByDoctorId = c["updatedByDoctorId"]?.toString() ?: "DOC-DEFAULT",
                                visitNumber = (c["visitNumber"] as? Number)?.toInt() ?: 1,
                                status = c["status"]?.toString() ?: "completed",
                                rawTranscript = c["rawTranscript"]?.toString() ?: "",
                                structuredDataJson = gson.toJson(c["structuredData"] ?: emptyMap<String, Any>()),
                                aiSuggestionsJson = gson.toJson(c["aiSuggestions"] ?: emptyMap<String, Any>()),
                                version = (c["version"] as? Number)?.toInt() ?: 1,
                                syncStatus = "synced",
                                isDeleted = (c["isDeleted"] as? Boolean) ?: false,
                                consultationDate = parseDateToMillis(c["consultationDate"] ?: c["createdAt"]),
                                lastModifiedAt = parseDateToMillis(c["lastModifiedAt"] ?: c["updatedAt"])
                            )
                            consultsToUpsert.add(entity)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    if (consultsToUpsert.isNotEmpty()) {
                        dao.upsertConsultations(consultsToUpsert)
                        pulledCount += consultsToUpsert.size
                    }
                }

                // Ingest Prescriptions
                if (updates.prescriptions.isNotEmpty()) {
                    val prescsToUpsert = mutableListOf<PrescriptionEntity>()
                    for (pr in updates.prescriptions) {
                        try {
                            val cId = pr["consultationId"]?.toString() ?: continue
                            val pId = pr["patientId"]?.toString() ?: continue
                            val entity = PrescriptionEntity(
                                consultationId = cId,
                                patientId = pId,
                                doctorId = pr["doctorId"]?.toString() ?: "DOC-DEFAULT",
                                medicationsJson = gson.toJson(pr["medications"] ?: emptyList<Any>()),
                                generalInstructions = pr["generalInstructions"]?.toString() ?: "",
                                diagnosisJson = gson.toJson(pr["diagnosis"] ?: emptyList<String>()),
                                followUpDate = pr["followUpDate"]?.let { parseDateToMillis(it) },
                                validUntil = pr["validUntil"]?.let { parseDateToMillis(it) },
                                version = (pr["version"] as? Number)?.toInt() ?: 1,
                                syncStatus = "synced",
                                isDeleted = (pr["isDeleted"] as? Boolean) ?: false,
                                lastModifiedAt = parseDateToMillis(pr["lastModifiedAt"] ?: pr["updatedAt"])
                            )
                            prescsToUpsert.add(entity)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    if (prescsToUpsert.isNotEmpty()) {
                        dao.upsertPrescriptions(prescsToUpsert)
                        pulledCount += prescsToUpsert.size
                    }
                }
            }

            // 5. Remove Synced Queue Items & Mark Entities Synced
            if (pendingItems.isNotEmpty()) {
                val queueIds = pendingItems.map { it.id }
                dao.deleteSyncItems(queueIds)
            }
            if (pushedPatientIds.isNotEmpty()) {
                dao.markPatientsAsSynced(pushedPatientIds)
            }
            if (pushedConsultIds.isNotEmpty()) {
                dao.markConsultationsAsSynced(pushedConsultIds)
            }
            if (pushedPrescIds.isNotEmpty()) {
                dao.markPrescriptionsAsSynced(pushedPrescIds)
            }

            // 6. Refresh Doctor's 7-Day Offline Lease
            val doctor = dao.getActiveDoctor()
            if (doctor != null) {
                dao.upsertDoctor(
                    doctor.copy(
                        leaseExpiresAt = System.currentTimeMillis() + DEFAULT_LEASE_MILLIS
                    )
                )
            }

            // 7. Store latest sync timestamp
            setLastSyncTimestamp(newSyncTimestamp)

            _isSyncing.value = false
            return SyncResult(
                success = true,
                pushedCount = pendingItems.size,
                pulledCount = pulledCount
            )
        } catch (e: Exception) {
            _isSyncing.value = false
            return SyncResult(success = false, error = e.localizedMessage ?: "Network error")
        }
    }
}

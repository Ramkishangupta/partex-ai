package com.voicecare.ai.data.local.dao

import androidx.room.*
import com.voicecare.ai.data.local.entities.ConsultationEntity
import com.voicecare.ai.data.local.entities.DoctorEntity
import com.voicecare.ai.data.local.entities.PatientEntity
import com.voicecare.ai.data.local.entities.PrescriptionEntity
import com.voicecare.ai.data.local.entities.SyncQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VoiceCareDao {

    // ──────────────── Doctors & Offline Auth ────────────────
    @Query("SELECT * FROM doctors WHERE isActiveSession = 1 LIMIT 1")
    fun getActiveDoctorFlow(): Flow<DoctorEntity?>

    @Query("SELECT * FROM doctors WHERE isActiveSession = 1 LIMIT 1")
    suspend fun getActiveDoctor(): DoctorEntity?

    @Query("SELECT * FROM doctors WHERE LOWER(email) = LOWER(:email) LIMIT 1")
    suspend fun getDoctorByEmail(email: String): DoctorEntity?

    @Upsert
    suspend fun upsertDoctor(doctor: DoctorEntity)

    @Query("UPDATE doctors SET isActiveSession = 0")
    suspend fun clearActiveDoctorSessions()

    // ──────────────── Patients ────────────────
    @Query("SELECT * FROM patients WHERE isDeleted = 0 ORDER BY lastModifiedAt DESC")
    fun getAllPatients(): Flow<List<PatientEntity>>

    @Query("""
        SELECT * FROM patients 
        WHERE isDeleted = 0 AND (
            name LIKE '%' || :query || '%' OR 
            patientId LIKE '%' || :query || '%' OR 
            phone LIKE '%' || :query || '%'
        )
        ORDER BY lastModifiedAt DESC
    """)
    fun searchPatients(query: String): Flow<List<PatientEntity>>

    @Query("SELECT * FROM patients WHERE patientId = :patientId LIMIT 1")
    suspend fun getPatientById(patientId: String): PatientEntity?

    @Upsert
    suspend fun upsertPatient(patient: PatientEntity)

    @Upsert
    suspend fun upsertPatients(patients: List<PatientEntity>)

    // ──────────────── Consultations ────────────────
    @Query("SELECT * FROM consultations WHERE isDeleted = 0 ORDER BY consultationDate DESC")
    fun getAllConsultations(): Flow<List<ConsultationEntity>>

    @Query("SELECT * FROM consultations WHERE patientId = :patientId AND isDeleted = 0 ORDER BY consultationDate DESC")
    fun getConsultationsForPatient(patientId: String): Flow<List<ConsultationEntity>>

    @Query("SELECT * FROM consultations WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getConsultationById(sessionId: String): ConsultationEntity?

    @Upsert
    suspend fun upsertConsultation(consultation: ConsultationEntity)

    @Upsert
    suspend fun upsertConsultations(consultations: List<ConsultationEntity>)

    // ──────────────── Prescriptions ────────────────
    @Query("SELECT * FROM prescriptions WHERE consultationId = :consultationId LIMIT 1")
    suspend fun getPrescriptionByConsultation(consultationId: String): PrescriptionEntity?

    @Query("SELECT * FROM prescriptions WHERE patientId = :patientId ORDER BY lastModifiedAt DESC")
    fun getPrescriptionsForPatient(patientId: String): Flow<List<PrescriptionEntity>>

    @Upsert
    suspend fun upsertPrescription(prescription: PrescriptionEntity)

    @Upsert
    suspend fun upsertPrescriptions(prescriptions: List<PrescriptionEntity>)

    // ──────────────── Sync Queue ────────────────
    @Query("SELECT * FROM sync_queue WHERE status = 'pending' ORDER BY createdAt ASC")
    suspend fun getPendingSyncItems(): List<SyncQueueEntity>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'pending'")
    fun getPendingSyncCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueueSyncItem(item: SyncQueueEntity)

    @Delete
    suspend fun deleteSyncItem(item: SyncQueueEntity)

    @Query("DELETE FROM sync_queue WHERE id IN (:ids)")
    suspend fun deleteSyncItems(ids: List<Long>)

    @Query("UPDATE patients SET syncStatus = 'synced' WHERE patientId IN (:ids)")
    suspend fun markPatientsAsSynced(ids: List<String>)

    @Query("UPDATE consultations SET syncStatus = 'synced' WHERE sessionId IN (:ids)")
    suspend fun markConsultationsAsSynced(ids: List<String>)

    @Query("UPDATE prescriptions SET syncStatus = 'synced' WHERE consultationId IN (:ids)")
    suspend fun markPrescriptionsAsSynced(ids: List<String>)

    @Query("SELECT * FROM patients WHERE isDeleted = 0")
    suspend fun getAllPatientsList(): List<PatientEntity>

    @Query("SELECT * FROM consultations WHERE isDeleted = 0")
    suspend fun getAllConsultationsList(): List<ConsultationEntity>
}

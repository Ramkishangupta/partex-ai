package com.voicecare.ai.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local SQLite representation of Patient record.
 * 100% Schema parity with server MongoDB Patient model.
 */
@Entity(tableName = "patients")
data class PatientEntity(
    @PrimaryKey
    val patientId: String,
    val doctorId: String = "DOC-DEFAULT",
    val updatedByDoctorId: String = "DOC-DEFAULT",
    val name: String,
    val age: Int = 0,
    val gender: String = "other",
    val phone: String = "",
    val address: String = "",
    val bloodGroup: String = "",
    val allergies: String = "[]",          // JSON Array
    val medicalHistory: String = "[]",     // JSON Array
    val version: Int = 1,
    val syncStatus: String = "synced",      // 'synced', 'pending', 'conflict'
    val isDeleted: Boolean = false,
    val lastModifiedAt: Long = System.currentTimeMillis()
)

/**
 * Local SQLite representation of Consultation encounter.
 * 100% Schema parity with server MongoDB Consultation model.
 */
@Entity(tableName = "consultations")
data class ConsultationEntity(
    @PrimaryKey
    val sessionId: String,
    val patientId: String,
    val doctorId: String = "DOC-DEFAULT",
    val updatedByDoctorId: String = "DOC-DEFAULT",
    val visitNumber: Int = 1,
    val status: String = "completed",       // 'active', 'processing', 'completed'
    val rawTranscript: String = "",
    val structuredDataJson: String = "{}",  // Symptoms, Vitals, Medications JSON
    val aiSuggestionsJson: String = "{}",   // Differential Diagnoses, Warnings JSON
    val version: Int = 1,
    val syncStatus: String = "synced",      // 'synced', 'pending', 'conflict'
    val isDeleted: Boolean = false,
    val consultationDate: Long = System.currentTimeMillis(),
    val lastModifiedAt: Long = System.currentTimeMillis()
)

/**
 * Local SQLite representation of Digital Prescription.
 */
@Entity(tableName = "prescriptions")
data class PrescriptionEntity(
    @PrimaryKey
    val consultationId: String,
    val patientId: String,
    val doctorId: String = "DOC-DEFAULT",
    val medicationsJson: String = "[]",     // Array of {name, dosage, frequency, duration, route}
    val generalInstructions: String = "",
    val diagnosisJson: String = "[]",
    val followUpDate: Long? = null,
    val validUntil: Long? = null,
    val version: Int = 1,
    val syncStatus: String = "synced",
    val isDeleted: Boolean = false,
    val lastModifiedAt: Long = System.currentTimeMillis()
)

/**
 * Offline Sync Queue: tracks local mutations waiting to be uploaded to Cloud MongoDB.
 */
@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val entityType: String,                 // 'patient', 'consultation', 'prescription'
    val entityId: String,
    val action: String,                     // 'INSERT', 'UPDATE', 'DELETE'
    val payloadJson: String,
    val status: String = "pending",         // 'pending', 'in_progress', 'failed'
    val retryCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Local representation of Doctor profile & verified session.
 * Verified online against hosted MongoDB on first-time login,
 * then cached locally to enable 100% offline authentication.
 */
@Entity(tableName = "doctors")
data class DoctorEntity(
    @PrimaryKey
    val doctorId: String,
    val name: String,
    val email: String,
    val specialization: String = "General Physician",
    val token: String = "",
    val passwordHash: String = "",
    val isActiveSession: Boolean = true,
    val isRevoked: Boolean = false,
    val leaseExpiresAt: Long = System.currentTimeMillis() + (7L * 24 * 60 * 60 * 1000), // 7-Day offline grace period
    val lastLoginAt: Long = System.currentTimeMillis()
)

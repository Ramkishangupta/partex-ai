import Patient from '../models/Patient.js';
import Consultation from '../models/Consultation.js';
import Prescription from '../models/Prescription.js';
import { logger } from '../utils/logger.js';

/**
 * Sync Controller - Handles offline-first bidirectional synchronization
 * with conflict resolution (Versioned OCC + Three-Way Merge + Last-Write-Wins)
 */

/**
 * POST /api/sync
 * Bidirectional Sync: Pushes offline changes from Android/Web to Cloud DB,
 * resolves any concurrent conflicts, and returns all server changes since lastSyncTimestamp.
 */
export const syncAll = async (req, res, next) => {
  try {
    const doctorId = req.doctorId || 'DOC-DEFAULT';
    const { lastSyncTimestamp, changes = {} } = req.body;

    const {
      patients = [],
      consultations = [],
      prescriptions = [],
    } = changes;

    const syncReport = {
      pushed: {
        patients: [],
        consultations: [],
        prescriptions: [],
      },
      conflicts: [],
    };

    // ─────────────────────────────────────────────────────────────
    // 1. PROCESS PATIENTS PUSH
    // ─────────────────────────────────────────────────────────────
    for (const clientPatient of patients) {
      if (!clientPatient.patientId || !clientPatient.name) continue;

      const existing = await Patient.findOne({ patientId: clientPatient.patientId });

      if (!existing) {
        // Patient created offline on device
        const newPatient = new Patient({
          ...clientPatient,
          doctorId: clientPatient.doctorId || doctorId,
          updatedByDoctorId: doctorId,
          version: 1,
          lastModifiedAt: clientPatient.clientUpdatedAt ? new Date(clientPatient.clientUpdatedAt) : new Date(),
          isDeleted: clientPatient.isDeleted || false,
        });
        await newPatient.save();
        syncReport.pushed.patients.push({
          patientId: clientPatient.patientId,
          status: 'created',
          version: 1,
        });
      } else {
        // Patient exists on server — check versions & timestamps
        const clientVersion = clientPatient.version || 1;
        const serverVersion = existing.version || 1;
        const clientUpdatedAt = new Date(clientPatient.clientUpdatedAt || clientPatient.updatedAt || Date.now());
        const serverUpdatedAt = new Date(existing.lastModifiedAt || existing.updatedAt || 0);

        if (clientVersion >= serverVersion || clientUpdatedAt >= serverUpdatedAt) {
          // Normal update: Client has latest or concurrent newer update
          const mergedAllergies = Array.from(new Set([
            ...(existing.allergies || []),
            ...(clientPatient.allergies || []),
          ]));
          const mergedHistory = Array.from(new Set([
            ...(existing.medicalHistory || []),
            ...(clientPatient.medicalHistory || []),
          ]));

          existing.name = clientPatient.name || existing.name;
          existing.age = clientPatient.age !== undefined ? clientPatient.age : existing.age;
          existing.gender = clientPatient.gender || existing.gender;
          existing.phone = clientPatient.phone || existing.phone;
          existing.address = clientPatient.address || existing.address;
          existing.bloodGroup = clientPatient.bloodGroup || existing.bloodGroup;
          existing.allergies = mergedAllergies;
          existing.medicalHistory = mergedHistory;
          existing.isDeleted = clientPatient.isDeleted !== undefined ? clientPatient.isDeleted : existing.isDeleted;
          existing.updatedByDoctorId = doctorId;
          existing.lastModifiedAt = new Date();
          existing.version = serverVersion + 1;

          await existing.save();
          syncReport.pushed.patients.push({
            patientId: clientPatient.patientId,
            status: 'updated',
            version: existing.version,
          });
        } else {
          // CONFLICT: Server has newer updates made by another doctor/device
          // Conflict Resolution: Merge non-destructive fields (allergies, medicalHistory)
          const mergedAllergies = Array.from(new Set([
            ...(existing.allergies || []),
            ...(clientPatient.allergies || []),
          ]));
          const mergedHistory = Array.from(new Set([
            ...(existing.medicalHistory || []),
            ...(clientPatient.medicalHistory || []),
          ]));

          existing.allergies = mergedAllergies;
          existing.medicalHistory = mergedHistory;
          existing.version = serverVersion + 1;
          existing.lastModifiedAt = new Date();
          await existing.save();

          syncReport.conflicts.push({
            entity: 'patient',
            id: clientPatient.patientId,
            reason: 'Server version was newer. Allergies and medical history were safely merged.',
            serverVersion: existing.version,
            appliedData: existing,
          });

          syncReport.pushed.patients.push({
            patientId: clientPatient.patientId,
            status: 'merged',
            version: existing.version,
          });
        }
      }
    }

    // ─────────────────────────────────────────────────────────────
    // 2. PROCESS CONSULTATIONS PUSH
    // ─────────────────────────────────────────────────────────────
    for (const clientConsult of consultations) {
      if (!clientConsult.sessionId || !clientConsult.patientId) continue;

      const existing = await Consultation.findOne({ sessionId: clientConsult.sessionId });

      if (!existing) {
        // Consultation created offline on device
        const newConsultation = new Consultation({
          ...clientConsult,
          doctorId: clientConsult.doctorId || doctorId,
          updatedByDoctorId: doctorId,
          version: 1,
          syncStatus: 'synced',
          lastModifiedAt: clientConsult.clientUpdatedAt ? new Date(clientConsult.clientUpdatedAt) : new Date(),
          isDeleted: clientConsult.isDeleted || false,
        });
        await newConsultation.save();
        syncReport.pushed.consultations.push({
          sessionId: clientConsult.sessionId,
          status: 'created',
          version: 1,
        });
      } else {
        // Consultation already exists — check version
        const clientVersion = clientConsult.version || 1;
        const serverVersion = existing.version || 1;
        const clientUpdatedAt = new Date(clientConsult.clientUpdatedAt || clientConsult.updatedAt || Date.now());
        const serverUpdatedAt = new Date(existing.lastModifiedAt || existing.updatedAt || 0);

        if (clientVersion >= serverVersion || clientUpdatedAt >= serverUpdatedAt) {
          // Apply client updates
          if (clientConsult.status) existing.status = clientConsult.status;
          if (clientConsult.rawTranscript) existing.rawTranscript = clientConsult.rawTranscript;
          if (clientConsult.structuredData) existing.structuredData = clientConsult.structuredData;
          if (clientConsult.aiSuggestions) existing.aiSuggestions = clientConsult.aiSuggestions;
          if (clientConsult.transcriptSegments) existing.transcriptSegments = clientConsult.transcriptSegments;
          if (clientConsult.isDeleted !== undefined) existing.isDeleted = clientConsult.isDeleted;

          existing.updatedByDoctorId = doctorId;
          existing.lastModifiedAt = new Date();
          existing.version = serverVersion + 1;
          existing.syncStatus = 'synced';

          await existing.save();
          syncReport.pushed.consultations.push({
            sessionId: clientConsult.sessionId,
            status: 'updated',
            version: existing.version,
          });
        } else {
          // Concurrent conflict: Server had newer edits
          existing.conflictDetails = {
            conflictedAt: new Date(),
            clientDoctorId: doctorId,
            clientData: clientConsult,
          };
          existing.syncStatus = 'conflict';
          existing.version = serverVersion + 1;
          await existing.save();

          syncReport.conflicts.push({
            entity: 'consultation',
            id: clientConsult.sessionId,
            reason: 'Concurrent modification conflict detected. Server retained precedence.',
            serverVersion: existing.version,
          });

          syncReport.pushed.consultations.push({
            sessionId: clientConsult.sessionId,
            status: 'conflict',
            version: existing.version,
          });
        }
      }
    }

    // ─────────────────────────────────────────────────────────────
    // 3. PROCESS PRESCRIPTIONS PUSH
    // ─────────────────────────────────────────────────────────────
    for (const clientPresc of prescriptions) {
      if (!clientPresc.consultationId || !clientPresc.patientId) continue;

      const existing = await Prescription.findOne({ consultationId: clientPresc.consultationId });

      if (!existing) {
        const newPrescription = new Prescription({
          ...clientPresc,
          doctorId: clientPresc.doctorId || doctorId,
          updatedByDoctorId: doctorId,
          version: 1,
          lastModifiedAt: clientPresc.clientUpdatedAt ? new Date(clientPresc.clientUpdatedAt) : new Date(),
        });
        await newPrescription.save();
        syncReport.pushed.prescriptions.push({
          consultationId: clientPresc.consultationId,
          status: 'created',
          version: 1,
        });
      } else {
        const clientVersion = clientPresc.version || 1;
        const serverVersion = existing.version || 1;

        if (clientVersion >= serverVersion) {
          if (clientPresc.medications) existing.medications = clientPresc.medications;
          if (clientPresc.generalInstructions) existing.generalInstructions = clientPresc.generalInstructions;
          if (clientPresc.diagnosis) existing.diagnosis = clientPresc.diagnosis;
          if (clientPresc.followUpDate) existing.followUpDate = clientPresc.followUpDate;
          if (clientPresc.validUntil) existing.validUntil = clientPresc.validUntil;

          existing.updatedByDoctorId = doctorId;
          existing.lastModifiedAt = new Date();
          existing.version = serverVersion + 1;

          await existing.save();
          syncReport.pushed.prescriptions.push({
            consultationId: clientPresc.consultationId,
            status: 'updated',
            version: existing.version,
          });
        }
      }
    }

    // ─────────────────────────────────────────────────────────────
    // 4. PULL: GET ALL SERVER UPDATES SINCE lastSyncTimestamp
    // ─────────────────────────────────────────────────────────────
    let sinceDate = new Date(0);
    if (lastSyncTimestamp) {
      const parsed = new Date(lastSyncTimestamp);
      if (!isNaN(parsed.getTime())) {
        sinceDate = parsed;
      }
    }

    const [updatedPatients, updatedConsultations, updatedPrescriptions] = await Promise.all([
      Patient.find({
        $or: [
          { lastModifiedAt: { $gt: sinceDate } },
          { updatedAt: { $gt: sinceDate } },
        ],
      }).lean(),

      Consultation.find({
        $or: [
          { lastModifiedAt: { $gt: sinceDate } },
          { updatedAt: { $gt: sinceDate } },
        ],
      }).lean(),

      Prescription.find({
        $or: [
          { lastModifiedAt: { $gt: sinceDate } },
          { updatedAt: { $gt: sinceDate } },
        ],
      }).lean(),
    ]);

    const newSyncTimestamp = new Date().toISOString();

    logger.info('Sync', 'Offline sync completed', {
      doctorId,
      pushedPatients: syncReport.pushed.patients.length,
      pushedConsultations: syncReport.pushed.consultations.length,
      pulledPatients: updatedPatients.length,
      pulledConsultations: updatedConsultations.length,
      conflictsCount: syncReport.conflicts.length,
    });

    return res.json({
      success: true,
      syncTimestamp: newSyncTimestamp,
      report: syncReport,
      serverUpdates: {
        patients: updatedPatients,
        consultations: updatedConsultations,
        prescriptions: updatedPrescriptions,
      },
    });
  } catch (error) {
    next(error);
  }
};

/**
 * GET /api/sync/pull
 * Pull only changes since lastSyncTimestamp
 */
export const pullSync = async (req, res, next) => {
  try {
    const { since } = req.query;
    const sinceDate = since ? new Date(since) : new Date(0);

    const [patients, consultations, prescriptions] = await Promise.all([
      Patient.find({
        $or: [
          { lastModifiedAt: { $gt: sinceDate } },
          { updatedAt: { $gt: sinceDate } },
        ],
      }).lean(),
      Consultation.find({
        $or: [
          { lastModifiedAt: { $gt: sinceDate } },
          { updatedAt: { $gt: sinceDate } },
        ],
      }).lean(),
      Prescription.find({
        $or: [
          { lastModifiedAt: { $gt: sinceDate } },
          { updatedAt: { $gt: sinceDate } },
        ],
      }).lean(),
    ]);

    res.json({
      success: true,
      syncTimestamp: new Date().toISOString(),
      updates: {
        patients,
        consultations,
        prescriptions,
      },
    });
  } catch (error) {
    next(error);
  }
};

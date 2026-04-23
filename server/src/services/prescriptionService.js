import Consultation from '../models/Consultation.js';
import Prescription from '../models/Prescription.js';
import { logger } from '../utils/logger.js';

/**
 * Prescription Service - Auto-generate prescriptions from structured data
 */

/**
 * Generate a prescription from consultation structured data
 */
export const generatePrescription = async (consultationId, doctorId) => {
  const existingPrescription = await Prescription.findOne({ consultationId, doctorId });
  if (existingPrescription) {
    return existingPrescription;
  }

  const consultation = await Consultation.findOne({ sessionId: consultationId, doctorId });
  if (!consultation) {
    throw new Error('Consultation not found');
  }

  if (!consultation.structuredData?.medications?.length) {
    throw new Error('No medications found in consultation data');
  }

  const prescription = new Prescription({
    consultationId: consultation.sessionId,
    patientId: consultation.patientId,
    doctorId: consultation.doctorId,
    medications: consultation.structuredData.medications.map((m) => ({
      name: m.name,
      dosage: m.dosage,
      frequency: m.frequency,
      duration: m.duration,
      route: m.route,
      instructions: m.notes || '',
    })),
    diagnosis: consultation.structuredData.diagnosis || [],
    generalInstructions: consultation.structuredData.followUp || '',
    followUpDate: calculateFollowUpDate(consultation.structuredData.followUp),
    isAIGenerated: true,
    validUntil: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000), // 30 days
  });

  await prescription.save();
  logger.info('PrescriptionService', 'Prescription generated', {
    consultationId,
    doctorId,
    patientId: consultation.patientId,
  });
  return prescription;
};

/**
 * Get prescriptions for a patient
 */
export const getPatientPrescriptions = async (patientId, doctorId) => {
  return Prescription.find({ patientId, doctorId })
    .sort({ createdAt: -1 })
    .lean();
};

/**
 * Try to parse a follow-up date from the follow-up string
 */
function calculateFollowUpDate(followUpText) {
  if (!followUpText) return null;

  const text = followUpText.toLowerCase();
  const now = new Date();

  // Match patterns like "after 1 week", "in 3 days", "2 weeks"
  const match = text.match(/(\d+)\s*(day|week|month)/);
  if (match) {
    const num = parseInt(match[1]);
    const unit = match[2];
    if (unit.startsWith('day')) now.setDate(now.getDate() + num);
    else if (unit.startsWith('week')) now.setDate(now.getDate() + num * 7);
    else if (unit.startsWith('month')) now.setMonth(now.getMonth() + num);
    return now;
  }

  return null;
}

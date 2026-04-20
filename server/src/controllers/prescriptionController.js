import { generatePrescription, getPatientPrescriptions } from '../services/prescriptionService.js';

// POST /api/prescriptions - Generate prescription from consultation
export const createPrescription = async (req, res, next) => {
  try {
    const doctorId = req.doctorId || 'DOC-DEFAULT';
    const { consultationId } = req.body;

    if (!consultationId) {
      return res.status(400).json({ error: 'Consultation ID (sessionId) is required' });
    }

    const prescription = await generatePrescription(consultationId, doctorId);
    res.status(201).json({ success: true, prescription });
  } catch (error) {
    next(error);
  }
};

// GET /api/prescriptions/patient/:patientId - Get patient's prescriptions
export const getPrescriptionsByPatient = async (req, res, next) => {
  try {
    const doctorId = req.doctorId || 'DOC-DEFAULT';
    const prescriptions = await getPatientPrescriptions(req.params.patientId, doctorId);
    res.json({ success: true, prescriptions });
  } catch (error) {
    next(error);
  }
};

// GET /api/prescriptions/:id - Get a specific prescription
export const getPrescriptionById = async (req, res, next) => {
  try {
    const doctorId = req.doctorId || 'DOC-DEFAULT';
    const { default: Prescription } = await import('../models/Prescription.js');
    const prescription = await Prescription.findOne({ _id: req.params.id, doctorId }).lean();
    if (!prescription) {
      return res.status(404).json({ error: 'Prescription not found' });
    }
    res.json({ success: true, prescription });
  } catch (error) {
    next(error);
  }
};

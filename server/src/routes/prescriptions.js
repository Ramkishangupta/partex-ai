import { Router } from 'express';
import { generatePrescription, getPatientPrescriptions } from '../services/prescriptionService.js';

const router = Router();

// POST /api/prescriptions - Generate prescription from consultation
router.post('/', async (req, res, next) => {
  try {
    const { consultationId } = req.body;

    if (!consultationId) {
      return res.status(400).json({ error: 'Consultation ID (sessionId) is required' });
    }

    const prescription = await generatePrescription(consultationId);
    res.status(201).json({ success: true, prescription });
  } catch (error) {
    next(error);
  }
});

// GET /api/prescriptions/patient/:patientId - Get patient's prescriptions
router.get('/patient/:patientId', async (req, res, next) => {
  try {
    const prescriptions = await getPatientPrescriptions(req.params.patientId);
    res.json({ success: true, prescriptions });
  } catch (error) {
    next(error);
  }
});

// GET /api/prescriptions/:id - Get a specific prescription
router.get('/:id', async (req, res, next) => {
  try {
    const { default: Prescription } = await import('../models/Prescription.js');
    const prescription = await Prescription.findById(req.params.id).lean();
    if (!prescription) {
      return res.status(404).json({ error: 'Prescription not found' });
    }
    res.json({ success: true, prescription });
  } catch (error) {
    next(error);
  }
});

export default router;

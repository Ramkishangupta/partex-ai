import { Router } from 'express';
import Patient from '../models/Patient.js';
import { generatePatientId } from '../utils/idGenerator.js';

const router = Router();

// POST /api/patients - Create a new patient
router.post('/', async (req, res, next) => {
  try {
    const { name, age, gender, phone, address, bloodGroup, allergies, medicalHistory } = req.body;

    if (!name) {
      return res.status(400).json({ error: 'Patient name is required' });
    }

    const patient = new Patient({
      patientId: generatePatientId(),
      name,
      age,
      gender,
      phone,
      address,
      bloodGroup,
      allergies: allergies || [],
      medicalHistory: medicalHistory || [],
    });

    await patient.save();
    res.status(201).json({ success: true, patient });
  } catch (error) {
    next(error);
  }
});

// GET /api/patients - List all patients (with optional search)
router.get('/', async (req, res, next) => {
  try {
    const { q, page = 1, limit = 20 } = req.query;
    const skip = (page - 1) * limit;

    let query = {};
    if (q) {
      query = {
        $or: [
          { name: { $regex: q, $options: 'i' } },
          { patientId: { $regex: q, $options: 'i' } },
          { phone: { $regex: q, $options: 'i' } },
        ],
      };
    }

    const [patients, total] = await Promise.all([
      Patient.find(query).sort({ updatedAt: -1 }).skip(skip).limit(parseInt(limit)).lean(),
      Patient.countDocuments(query),
    ]);

    res.json({
      success: true,
      patients,
      pagination: { page: parseInt(page), limit: parseInt(limit), total, pages: Math.ceil(total / limit) },
    });
  } catch (error) {
    next(error);
  }
});

// GET /api/patients/:id - Get a single patient by patientId
router.get('/:id', async (req, res, next) => {
  try {
    const patient = await Patient.findOne({ patientId: req.params.id }).lean();
    if (!patient) {
      return res.status(404).json({ error: 'Patient not found' });
    }
    res.json({ success: true, patient });
  } catch (error) {
    next(error);
  }
});

// PUT /api/patients/:id - Update patient
router.put('/:id', async (req, res, next) => {
  try {
    const updates = req.body;
    delete updates.patientId; // Don't allow ID changes

    const patient = await Patient.findOneAndUpdate(
      { patientId: req.params.id },
      updates,
      { new: true, runValidators: true }
    );

    if (!patient) {
      return res.status(404).json({ error: 'Patient not found' });
    }

    res.json({ success: true, patient });
  } catch (error) {
    next(error);
  }
});

// GET /api/patients/:id/history - Get full visit history
router.get('/:id/history', async (req, res, next) => {
  try {
    const { default: Consultation } = await import('../models/Consultation.js');

    const patient = await Patient.findOne({ patientId: req.params.id }).lean();
    if (!patient) {
      return res.status(404).json({ error: 'Patient not found' });
    }

    const consultations = await Consultation.find({ patientId: req.params.id })
      .sort({ consultationDate: -1 })
      .lean();

    res.json({
      success: true,
      patient,
      totalVisits: consultations.length,
      consultations,
    });
  } catch (error) {
    next(error);
  }
});

export default router;

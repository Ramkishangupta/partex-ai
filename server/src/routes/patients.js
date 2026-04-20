import { Router } from 'express';
import {
  createPatient,
  getPatients,
  getPatientById,
  updatePatient,
  getPatientHistory,
  getPatientReport,
} from '../controllers/patientController.js';

const router = Router();

// POST /api/patients - Create a new patient
router.post('/', createPatient);

// GET /api/patients - List all patients (with optional search)
router.get('/', getPatients);

// GET /api/patients/:id - Get a single patient by patientId
router.get('/:id/report', getPatientReport);

// GET /api/patients/:id - Get a single patient by patientId
router.get('/:id', getPatientById);

// PUT /api/patients/:id - Update patient
router.put('/:id', updatePatient);

// GET /api/patients/:id/history - Get full visit history
router.get('/:id/history', getPatientHistory);

export default router;

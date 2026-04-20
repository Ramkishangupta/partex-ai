import { Router } from 'express';
import Doctor from '../models/Doctor.js';
import { generateDoctorId } from '../utils/idGenerator.js';
import { generateToken } from '../middleware/auth.js';

const router = Router();

// POST /api/auth/register - Register a new doctor
router.post('/register', async (req, res, next) => {
  try {
    const { name, email, password, specialization, licenseNumber } = req.body;

    if (!name || !email || !password) {
      return res.status(400).json({ error: 'Name, email, and password are required' });
    }

    // Check if email already exists
    const existing = await Doctor.findOne({ email: email.toLowerCase() });
    if (existing) {
      return res.status(409).json({ error: 'Email already registered' });
    }

    // For hackathon: store password as-is (production would hash with bcrypt)
    const doctor = new Doctor({
      doctorId: generateDoctorId(),
      name,
      email: email.toLowerCase(),
      password, // TODO: Hash with bcrypt in production
      specialization,
      licenseNumber,
    });

    await doctor.save();

    const token = generateToken(doctor);

    res.status(201).json({
      success: true,
      doctor: {
        doctorId: doctor.doctorId,
        name: doctor.name,
        email: doctor.email,
        specialization: doctor.specialization,
      },
      token,
    });
  } catch (error) {
    next(error);
  }
});

// POST /api/auth/login - Doctor login
router.post('/login', async (req, res, next) => {
  try {
    const { email, password } = req.body;

    if (!email || !password) {
      return res.status(400).json({ error: 'Email and password are required' });
    }

    const doctor = await Doctor.findOne({ email: email.toLowerCase() });
    if (!doctor || doctor.password !== password) {
      return res.status(401).json({ error: 'Invalid credentials' });
    }

    const token = generateToken(doctor);

    res.json({
      success: true,
      doctor: {
        doctorId: doctor.doctorId,
        name: doctor.name,
        email: doctor.email,
        specialization: doctor.specialization,
      },
      token,
    });
  } catch (error) {
    next(error);
  }
});

export default router;

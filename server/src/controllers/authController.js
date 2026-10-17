import jwt from 'jsonwebtoken';
import Doctor from '../models/Doctor.js';
import { generateDoctorId } from '../utils/idGenerator.js';
import { generateToken } from '../middleware/auth.js';
import { logger } from '../utils/logger.js';

// POST /api/auth/register - Register a new doctor
export const register = async (req, res, next) => {
  try {
    const { name, email, password, specialization, licenseNumber } = req.body;

    if (!name || !email || !password) {
      return res.status(400).json({ error: 'Name, email, and password are required' });
    }

    // Check if email already exists
    const existing = await Doctor.findOne({ email: email.toLowerCase() });
    if (existing) {
      logger.warn('AuthService', 'Registration blocked: email already exists', {
        email: email.toLowerCase(),
      });
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
    logger.info('AuthService', 'Doctor registration successful', {
      doctorId: doctor.doctorId,
      email: doctor.email,
    });

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
};

// POST /api/auth/login - Doctor login
export const login = async (req, res, next) => {
  try {
    const { email, password } = req.body;

    if (!email || !password) {
      return res.status(400).json({ error: 'Email and password are required' });
    }

    const doctor = await Doctor.findOne({ email: email.toLowerCase() });
    if (!doctor || doctor.password !== password) {
      logger.warn('AuthService', 'Login failed: invalid credentials', {
        email: email.toLowerCase(),
      });
      return res.status(401).json({ error: 'Invalid credentials' });
    }

    // Check if account was revoked / deleted
    if (doctor.isDeleted || doctor.isActive === false) {
      logger.warn('AuthService', 'Login blocked: doctor account is deactivated or revoked', {
        doctorId: doctor.doctorId,
        email: doctor.email,
      });
      return res.status(401).json({
        error: 'This doctor account has been deactivated or removed from the hospital registry.',
        code: 'ACCOUNT_REVOKED',
      });
    }

    const token = generateToken(doctor);
    logger.info('AuthService', 'User login successful', {
      userId: doctor.doctorId,
    });

    res.json({
      success: true,
      doctor: {
        doctorId: doctor.doctorId,
        name: doctor.name,
        email: doctor.email,
        specialization: doctor.specialization,
      },
      token,
      leaseDays: 7,
    });
  } catch (error) {
    next(error);
  }
};

// GET /api/auth/verify - Verify doctor token and central status (renews 7-day offline lease)
export const verifyDoctor = async (req, res, next) => {
  try {
    const authHeader = req.headers.authorization;
    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      return res.status(401).json({
        valid: false,
        error: 'Authentication token required',
        code: 'NO_TOKEN',
      });
    }

    const token = authHeader.split(' ')[1];
    let decoded;
    try {
      decoded = jwt.verify(token, process.env.JWT_SECRET || 'voicecare_jwt_secret');
    } catch (jwtErr) {
      return res.status(401).json({
        valid: false,
        error: 'Invalid or expired token',
        code: 'INVALID_TOKEN',
      });
    }

    const doctor = await Doctor.findOne({ doctorId: decoded.doctorId });
    if (!doctor || doctor.isDeleted || doctor.isActive === false) {
      logger.warn('AuthService', 'Doctor handshake failed: account revoked or removed', {
        doctorId: decoded.doctorId,
      });
      return res.status(401).json({
        valid: false,
        error: 'Doctor account has been revoked or removed from central registry',
        code: 'ACCOUNT_REVOKED',
      });
    }

    const renewedToken = generateToken(doctor);

    res.json({
      success: true,
      valid: true,
      doctor: {
        doctorId: doctor.doctorId,
        name: doctor.name,
        email: doctor.email,
        specialization: doctor.specialization,
      },
      token: renewedToken,
      leaseDays: 7,
    });
  } catch (error) {
    next(error);
  }
};

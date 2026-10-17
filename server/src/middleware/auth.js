import jwt from 'jsonwebtoken';
import dotenv from 'dotenv';
import Doctor from '../models/Doctor.js';

dotenv.config({ path: '../.env' });

/**
 * Auth middleware - Verifies JWT token and checks central Doctor status
 */
export const authenticate = async (req, res, next) => {
  try {
    const authHeader = req.headers.authorization;
    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      // In development without any header, allow fallback
      if (process.env.NODE_ENV === 'development') {
        req.doctorId = 'DOC-DEFAULT';
        return next();
      }
      return res.status(401).json({ error: 'Authentication required', code: 'NO_TOKEN' });
    }

    const token = authHeader.split(' ')[1];
    const decoded = jwt.verify(token, process.env.JWT_SECRET || 'voicecare_jwt_secret');

    // Central Registry Check: Ensure doctor has not been deleted or deactivated
    const doctor = await Doctor.findOne({ doctorId: decoded.doctorId });
    if (!doctor || doctor.isDeleted || doctor.isActive === false) {
      return res.status(401).json({
        error: 'Doctor account has been revoked or deleted from central registry',
        code: 'ACCOUNT_REVOKED',
      });
    }

    req.doctorId = doctor.doctorId;
    req.doctorName = doctor.name;
    req.doctor = doctor;
    next();
  } catch (error) {
    if (process.env.NODE_ENV === 'development' && !req.headers.authorization) {
      req.doctorId = 'DOC-DEFAULT';
      return next();
    }
    res.status(401).json({ error: 'Invalid or expired token', code: 'INVALID_TOKEN' });
  }
};

/**
 * Generate JWT token
 */
export const generateToken = (doctor) => {
  return jwt.sign(
    { doctorId: doctor.doctorId, name: doctor.name, email: doctor.email },
    process.env.JWT_SECRET || 'voicecare_jwt_secret',
    { expiresIn: process.env.JWT_EXPIRES_IN || '24h' }
  );
};

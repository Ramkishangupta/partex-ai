import jwt from 'jsonwebtoken';
import dotenv from 'dotenv';

dotenv.config({ path: '../.env' });

/**
 * Auth middleware - Verifies JWT token
 * For hackathon, this is optional/lightweight
 */
export const authenticate = (req, res, next) => {
  try {
    const authHeader = req.headers.authorization;
    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      // In development, allow unauthenticated requests
      if (process.env.NODE_ENV === 'development') {
        req.doctorId = 'DOC-DEFAULT';
        return next();
      }
      return res.status(401).json({ error: 'Authentication required' });
    }

    const token = authHeader.split(' ')[1];
    const decoded = jwt.verify(token, process.env.JWT_SECRET || 'voicecare_jwt_secret');
    req.doctorId = decoded.doctorId;
    req.doctorName = decoded.name;
    next();
  } catch (error) {
    if (process.env.NODE_ENV === 'development') {
      req.doctorId = 'DOC-DEFAULT';
      return next();
    }
    res.status(401).json({ error: 'Invalid or expired token' });
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

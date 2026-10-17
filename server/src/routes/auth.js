import { Router } from 'express';
import { register, login, verifyDoctor } from '../controllers/authController.js';

const router = Router();

// POST /api/auth/register - Register a new doctor
router.post('/register', register);

// POST /api/auth/login - Doctor login
router.post('/login', login);

// GET /api/auth/verify - Check central doctor status & renew offline lease
router.get('/verify', verifyDoctor);

export default router;

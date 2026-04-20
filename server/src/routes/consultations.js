import { Router } from 'express';
import multer from 'multer';
import {
  createConsultation,
  getConsultationById,
  getConsultations,
  updateConsultation,
  getConsultationReport,
} from '../controllers/consultationController.js';

const router = Router();

// Multer config for audio file uploads
const upload = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: 50 * 1024 * 1024 }, // 50MB max
  fileFilter: (req, file, cb) => {
    const allowedTypes = ['audio/wav', 'audio/webm', 'audio/ogg', 'audio/mp3', 'audio/mpeg', 'audio/mp4', 'audio/x-m4a'];
    if (allowedTypes.includes(file.mimetype) || file.mimetype.startsWith('audio/')) {
      cb(null, true);
    } else {
      cb(new Error('Only audio files are allowed'), false);
    }
  },
});

// POST /api/consultations - Start a new consultation (or process uploaded audio)
router.post('/', upload.single('audio'), createConsultation);

// GET /api/consultations/:sessionId - Get a consultation
router.get('/:sessionId', getConsultationById);

// GET /api/consultations/:sessionId/report - Get a readable encounter report
router.get('/:sessionId/report', getConsultationReport);

// GET /api/consultations - List recent consultations
router.get('/', getConsultations);

// PATCH /api/consultations/:sessionId - Update consultation (e.g., doctor edits)
router.patch('/:sessionId', updateConsultation);

export default router;

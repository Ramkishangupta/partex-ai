import { Router } from 'express';
import Consultation from '../models/Consultation.js';
import { generateSessionId } from '../utils/idGenerator.js';
import { extractMedicalData, generateDoctorAssist } from '../services/llmService.js';
import { transcribeAudioFile } from '../services/asrService.js';
import multer from 'multer';

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
router.post('/', upload.single('audio'), async (req, res, next) => {
  try {
    const { patientId, doctorId = 'DOC-DEFAULT', transcript } = req.body;

    if (!patientId) {
      return res.status(400).json({ error: 'Patient ID is required' });
    }

    const sessionId = generateSessionId();
    const visitCount = await Consultation.countDocuments({ patientId });

    // Case 1: Audio file uploaded — full pipeline
    if (req.file) {
      // Step 1: Transcribe
      const asrResult = await transcribeAudioFile(req.file.buffer, req.file.mimetype);

      // Step 2: Extract medical data
      const structuredData = await extractMedicalData(
        asrResult.transcript,
        [asrResult.detectedLanguage]
      );

      // Step 3: Generate doctor assist
      let aiSuggestions = {};
      try {
        aiSuggestions = await generateDoctorAssist(structuredData);
      } catch {}

      // Step 4: Save
      const consultation = new Consultation({
        patientId,
        doctorId,
        sessionId,
        visitNumber: visitCount + 1,
        status: 'completed',
        rawTranscript: asrResult.transcript,
        detectedLanguages: [asrResult.detectedLanguage],
        transcriptSegments: asrResult.segments || [],
        structuredData,
        aiSuggestions,
        audioMetadata: {
          durationSeconds: Math.round(asrResult.metadata?.duration || 0),
          format: req.file.mimetype,
          sampleRate: 16000,
        },
      });
      await consultation.save();

      return res.status(201).json({
        success: true,
        consultation: {
          sessionId,
          patientId,
          visitNumber: visitCount + 1,
          transcript: asrResult.transcript,
          structuredData,
          aiSuggestions,
          languages: [asrResult.detectedLanguage],
        },
      });
    }

    // Case 2: Raw transcript provided — extraction only
    if (transcript) {
      const structuredData = await extractMedicalData(transcript);

      // Doctor assist is optional (saves 1 API call) — enable with ?assist=true
      let aiSuggestions = {};
      if (req.query.assist === 'true') {
        try {
          aiSuggestions = await generateDoctorAssist(structuredData);
        } catch {}
      }

      const consultation = new Consultation({
        patientId,
        doctorId,
        sessionId,
        visitNumber: visitCount + 1,
        status: 'completed',
        rawTranscript: transcript,
        structuredData,
        aiSuggestions,
      });
      await consultation.save();

      return res.status(201).json({
        success: true,
        consultation: {
          sessionId,
          patientId,
          visitNumber: visitCount + 1,
          transcript,
          structuredData,
          aiSuggestions,
        },
      });
    }

    // Case 3: Just creating a session (for live recording via WebSocket)
    const consultation = new Consultation({
      patientId,
      doctorId,
      sessionId,
      visitNumber: visitCount + 1,
      status: 'active',
    });
    await consultation.save();

    res.status(201).json({
      success: true,
      consultation: {
        sessionId,
        patientId,
        visitNumber: visitCount + 1,
        status: 'active',
      },
    });
  } catch (error) {
    next(error);
  }
});

// GET /api/consultations/:sessionId - Get a consultation
router.get('/:sessionId', async (req, res, next) => {
  try {
    const consultation = await Consultation.findOne({ sessionId: req.params.sessionId }).lean();
    if (!consultation) {
      return res.status(404).json({ error: 'Consultation not found' });
    }
    res.json({ success: true, consultation });
  } catch (error) {
    next(error);
  }
});

// GET /api/consultations - List recent consultations
router.get('/', async (req, res, next) => {
  try {
    const { patientId, status, page = 1, limit = 20 } = req.query;
    const skip = (page - 1) * limit;

    const query = {};
    if (patientId) query.patientId = patientId;
    if (status) query.status = status;

    const [consultations, total] = await Promise.all([
      Consultation.find(query)
        .sort({ consultationDate: -1 })
        .skip(skip)
        .limit(parseInt(limit))
        .lean(),
      Consultation.countDocuments(query),
    ]);

    res.json({
      success: true,
      consultations,
      pagination: { page: parseInt(page), limit: parseInt(limit), total },
    });
  } catch (error) {
    next(error);
  }
});

// PATCH /api/consultations/:sessionId - Update consultation (e.g., doctor edits)
router.patch('/:sessionId', async (req, res, next) => {
  try {
    const updates = req.body;
    delete updates.sessionId;
    delete updates.patientId;

    const consultation = await Consultation.findOneAndUpdate(
      { sessionId: req.params.sessionId },
      { $set: updates },
      { new: true }
    );

    if (!consultation) {
      return res.status(404).json({ error: 'Consultation not found' });
    }

    res.json({ success: true, consultation });
  } catch (error) {
    next(error);
  }
});

export default router;

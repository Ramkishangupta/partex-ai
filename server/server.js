import express from 'express';
import cors from 'cors';
import dotenv from 'dotenv';

// Load environment variables from root .env
dotenv.config({ path: '../.env' });

import connectDB from './src/config/db.js';
import { errorHandler, notFoundHandler } from './src/middleware/errorHandler.js';
import { authenticate } from './src/middleware/auth.js';

// Route imports
import patientRoutes from './src/routes/patients.js';
import consultationRoutes from './src/routes/consultations.js';
import chatRoutes from './src/routes/chat.js';
import prescriptionRoutes from './src/routes/prescriptions.js';
import authRoutes from './src/routes/auth.js';
import { logger } from './src/utils/logger.js';

const app = express();

// ─── MIDDLEWARE ──────────────────────────────────────────
app.use(cors({
  origin: process.env.CLIENT_URL || '*',
  credentials: true,
}));
app.use(express.json({ limit: '50mb' }));
app.use(express.urlencoded({ extended: true, limit: '50mb' }));

// Request logging (development)
if (process.env.NODE_ENV === 'development') {
  app.use((req, res, next) => {
    logger.info('HTTP', 'Request received', {
      method: req.method,
      path: req.originalUrl,
    });
    next();
  });
}

// ─── ROUTES ─────────────────────────────────────────────
app.use('/api/auth', authRoutes);
app.use('/api/patients', authenticate, patientRoutes);
app.use('/api/consultations', authenticate, consultationRoutes);
app.use('/api/chat', authenticate, chatRoutes);
app.use('/api/prescriptions', authenticate, prescriptionRoutes);

// Health check
app.get('/api/health', (req, res) => {
  res.json({
    status: 'healthy',
    service: 'VoiceCare API',
    timestamp: new Date().toISOString(),
    env: process.env.NODE_ENV || 'development',
    services: {
      deepgram: !!process.env.DEEPGRAM_API_KEY && process.env.DEEPGRAM_API_KEY !== 'your_deepgram_api_key_here',
      groq: !!process.env.GROQ_API_KEY && process.env.GROQ_API_KEY !== 'your_groq_api_key_here',
      mongodb: true, // If we get here, DB is connected
    },
  });
});

// ─── ERROR HANDLING ─────────────────────────────────────
app.use(notFoundHandler);
app.use(errorHandler);

// ─── START SERVER ───────────────────────────────────────
const PORT = process.env.PORT || 5000;

const startServer = async () => {
  try {
    // Connect to MongoDB
    await connectDB();

    app.listen(PORT, () => {
      logger.info('Server', 'VoiceCare server started', {
        port: PORT,
        restApi: `http://localhost:${PORT}/api`,
        health: `http://localhost:${PORT}/api/health`,
        env: process.env.NODE_ENV || 'development',
      });
    });
  } catch (error) {
    logger.error('Server', 'Failed to start server', {
      error: error.message,
    });
    process.exit(1);
  }
};

startServer();

export { app };

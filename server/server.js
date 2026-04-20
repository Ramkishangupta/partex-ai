import express from 'express';
import http from 'http';
import { Server } from 'socket.io';
import cors from 'cors';
import dotenv from 'dotenv';

// Load environment variables from root .env
dotenv.config({ path: '../.env' });

import connectDB from './src/config/db.js';
import { setupAudioHandler } from './src/socket/audioHandler.js';
import { errorHandler, notFoundHandler } from './src/middleware/errorHandler.js';
import { authenticate } from './src/middleware/auth.js';

// Route imports
import patientRoutes from './src/routes/patients.js';
import consultationRoutes from './src/routes/consultations.js';
import chatRoutes from './src/routes/chat.js';
import prescriptionRoutes from './src/routes/prescriptions.js';
import authRoutes from './src/routes/auth.js';

const app = express();
const server = http.createServer(app);

// Socket.io setup with CORS
const io = new Server(server, {
  cors: {
    origin: process.env.CLIENT_URL || '*',
    methods: ['GET', 'POST'],
  },
  maxHttpBufferSize: 5 * 1024 * 1024, // 5MB for audio chunks
});

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
    console.log(`${req.method} ${req.originalUrl}`);
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
      gemini: !!process.env.GEMINI_API_KEY && process.env.GEMINI_API_KEY !== 'your_gemini_api_key_here',
      mongodb: true, // If we get here, DB is connected
    },
  });
});

// ─── ERROR HANDLING ─────────────────────────────────────
app.use(notFoundHandler);
app.use(errorHandler);

// ─── SETUP WEBSOCKET ────────────────────────────────────
setupAudioHandler(io);

// ─── START SERVER ───────────────────────────────────────
const PORT = process.env.PORT || 5000;

const startServer = async () => {
  try {
    // Connect to MongoDB
    await connectDB();

    server.listen(PORT, () => {
      console.log(`
╔══════════════════════════════════════════════╗
║                                              ║
║   🏥 VoiceCare Server Running                ║
║                                              ║
║   REST API:  http://localhost:${PORT}/api      ║
║   WebSocket: ws://localhost:${PORT}            ║
║   Health:    http://localhost:${PORT}/api/health║
║                                              ║
║   Environment: ${(process.env.NODE_ENV || 'development').padEnd(28)}║
║                                              ║
╚══════════════════════════════════════════════╝
      `);
    });
  } catch (error) {
    console.error('❌ Failed to start server:', error);
    process.exit(1);
  }
};

startServer();

export { app, server, io };

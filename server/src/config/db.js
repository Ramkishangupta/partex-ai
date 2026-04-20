import mongoose from 'mongoose';
import dotenv from 'dotenv';
import { logger } from '../utils/logger.js';

dotenv.config({ path: '../.env' });

const connectDB = async () => {
  try {
    const conn = await mongoose.connect(process.env.MONGODB_URI || 'mongodb://localhost:27017/voicecare');
    logger.info('DB', 'MongoDB connected', {
      host: conn.connection.host,
    });
    return conn;
  } catch (error) {
    logger.error('DB', 'MongoDB connection failed', {
      error: error.message,
    });
    process.exit(1);
  }
};

export default connectDB;

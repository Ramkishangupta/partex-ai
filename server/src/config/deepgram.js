import { createClient } from '@deepgram/sdk';
import dotenv from 'dotenv';

dotenv.config({ path: '../.env' });

let deepgramClient = null;

export const getDeepgramClient = () => {
  if (!deepgramClient) {
    const apiKey = process.env.DEEPGRAM_API_KEY;
    if (!apiKey || apiKey === 'your_deepgram_api_key_here') {
      console.warn('⚠️  Deepgram API key not configured. ASR will not work.');
      return null;
    }
    deepgramClient = createClient(apiKey);
    console.log('✅ Deepgram client initialized');
  }
  return deepgramClient;
};

// Config for pre-recorded audio (file upload fallback)
export const DEEPGRAM_PRERECORDED_CONFIG = {
  model: 'nova-2',
  detect_language: true,
  smart_format: true,
  diarize: true,
  punctuate: true,
  paragraphs: true,
  utterances: true,
};

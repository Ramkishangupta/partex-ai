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

// Default config for live transcription
export const DEEPGRAM_LIVE_CONFIG = {
  model: 'nova-2',
  language: 'hi',           // Default to Hindi
  // detect_language: true, // EXPLICITLY REMOVED: Deepgram errors if language AND detect_language are both provided
  smart_format: true,        // Punctuation + formatting
  diarize: true,             // Speaker identification (doctor vs patient)
  punctuate: true,
  filler_words: false,       // Remove "um", "uh" etc.
  interim_results: true,     // Send partial results for live UI
  endpointing: 500,          // 500ms silence = end of utterance (handles noisy OPD)
  // encoding: 'linear16',    // REMOVED: Auto-detect WebM from browser
  // sample_rate: 16000,      // REMOVED: Auto-detect WebM from browser
  channels: 1,
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

import { getDeepgramClient, DEEPGRAM_PRERECORDED_CONFIG } from '../config/deepgram.js';

/**
 * ASR Service - Handles speech-to-text via Deepgram
 * Supports pre-recorded file processing.
 */

/**
 * Transcribe a pre-recorded audio file/buffer
 * Useful as fallback or for re-processing with better accuracy
 */
export const transcribeAudioFile = async (audioBuffer, mimetype = 'audio/wav') => {
  const client = getDeepgramClient();
  if (!client) {
    throw new Error('Deepgram client not initialized. Check API key.');
  }

  try {
    const { result } = await client.listen.prerecorded.transcribeFile(
      audioBuffer,
      {
        ...DEEPGRAM_PRERECORDED_CONFIG,
        mimetype,
      }
    );

    // Extract the full transcript
    const channels = result?.results?.channels || [];
    const alternatives = channels[0]?.alternatives || [];
    let transcript = alternatives[0]?.transcript || '';
    const confidence = alternatives[0]?.confidence || 0;
    const detectedLanguage = channels[0]?.detected_language || 'unknown';

    // Extract utterances with speaker info (diarization)
    const utterances = result?.results?.utterances || [];
    const segments = utterances.map((u) => ({
      speaker: `speaker_${u.speaker}`,
      text: u.transcript,
      startTime: u.start,
      endTime: u.end,
      confidence: u.confidence,
      language: detectedLanguage,
    }));

    // Some files return utterances but leave alternatives transcript empty.
    if (!transcript.trim() && segments.length > 0) {
      transcript = segments.map((s) => s.text).filter(Boolean).join(' ').trim();
    }

    return {
      transcript,
      confidence,
      detectedLanguage,
      segments,
      metadata: {
        duration: result?.metadata?.duration || 0,
        channels: result?.metadata?.channels || 1,
        modelInfo: result?.metadata?.model_info || {},
      },
    };
  } catch (error) {
    console.error('❌ Deepgram transcription error:', error);
    throw error;
  }
};

/**
 * Transcribe from a URL (e.g., cloud-stored audio)
 */
export const transcribeAudioURL = async (audioUrl) => {
  const client = getDeepgramClient();
  if (!client) {
    throw new Error('Deepgram client not initialized. Check API key.');
  }

  try {
    const { result } = await client.listen.prerecorded.transcribeUrl(
      { url: audioUrl },
      DEEPGRAM_PRERECORDED_CONFIG
    );

    const channels = result?.results?.channels || [];
    const alternatives = channels[0]?.alternatives || [];
    const transcript = alternatives[0]?.transcript || '';

    return {
      transcript,
      confidence: alternatives[0]?.confidence || 0,
      detectedLanguage: channels[0]?.detected_language || 'unknown',
    };
  } catch (error) {
    console.error('❌ Deepgram URL transcription error:', error);
    throw error;
  }
};

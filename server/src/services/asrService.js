import { getDeepgramClient, DEEPGRAM_LIVE_CONFIG, DEEPGRAM_PRERECORDED_CONFIG } from '../config/deepgram.js';

/**
 * ASR Service - Handles speech-to-text via Deepgram
 * Supports both live streaming and pre-recorded file processing
 */

/**
 * Create a live transcription stream connection
 * Returns the Deepgram live connection object for streaming audio
 */
export const createLiveTranscription = (callbacks = {}) => {
  const client = getDeepgramClient();
  if (!client) {
    throw new Error('Deepgram client not initialized. Check API key.');
  }

  const connection = client.listen.live(DEEPGRAM_LIVE_CONFIG);

  // Wire up callbacks
  connection.on('open', () => {
    console.log('🎤 Deepgram live connection opened');
    callbacks.onOpen?.();
  });

  connection.on('close', () => {
    console.log('🎤 Deepgram live connection closed');
    callbacks.onClose?.();
  });

  connection.on('transcript', (data) => {
    const transcript = data.channel?.alternatives?.[0];
    if (!transcript) return;

    const result = {
      text: transcript.transcript || '',
      confidence: transcript.confidence || 0,
      words: transcript.words || [],
      isFinal: data.is_final || false,
      speechFinal: data.speech_final || false,
      language: data.channel?.detected_language || transcript.detected_language || 'unknown',
    };

    // Only forward non-empty transcripts
    if (result.text.trim()) {
      if (result.isFinal) {
        callbacks.onFinalTranscript?.(result);
      } else {
        callbacks.onInterimTranscript?.(result);
      }
    }
  });

  connection.on('metadata', (data) => {
    callbacks.onMetadata?.(data);
  });

  connection.on('error', (error) => {
    console.error('❌ Deepgram error:', error);
    callbacks.onError?.(error);
  });

  return connection;
};

/**
 * Send audio data to a live transcription connection
 */
export const sendAudioChunk = (connection, audioBuffer) => {
  if (connection && connection.getReadyState() === 1) {
    connection.send(audioBuffer);
    return true;
  }
  return false;
};

/**
 * Close a live transcription connection
 */
export const closeLiveConnection = (connection) => {
  if (connection) {
    connection.finish();
  }
};

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
    const transcript = alternatives[0]?.transcript || '';
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

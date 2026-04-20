import { createLiveTranscription, sendAudioChunk, closeLiveConnection, transcribeAudioFile } from '../services/asrService.js';
import { extractMedicalData, generateDoctorAssist } from '../services/llmService.js';
import Consultation from '../models/Consultation.js';
import { generateSessionId } from '../utils/idGenerator.js';

/**
 * WebSocket Audio Handler
 * Manages the real-time audio ➜ transcript ➜ structured data pipeline
 * 
 * Flow:
 * 1. Client starts recording → audio:start event
 * 2. Client sends audio chunks → audio:chunk event
 * 3. Deepgram processes audio → sends interim/final transcripts back
 * 4. Client stops recording → audio:stop event
 * 5. Full transcript sent to Gemini → structured data extracted
 * 6. Data saved to MongoDB → result sent to client
 */

// Active sessions: Map<socketId, sessionState>
const activeSessions = new Map();

export const setupAudioHandler = (io) => {
  io.on('connection', (socket) => {
    console.log(`🔌 Client connected: ${socket.id}`);

    // ─── START RECORDING ───────────────────────────────────
    socket.on('audio:start', async (data) => {
      const { patientId, doctorId = 'DOC-DEFAULT' } = data;

      if (!patientId) {
        socket.emit('error', { code: 'MISSING_PATIENT_ID', message: 'Patient ID is required' });
        return;
      }

      const sessionId = generateSessionId();

      // Count previous visits for this patient
      const visitCount = await Consultation.countDocuments({ patientId });

      // Create consultation record
      const consultation = new Consultation({
        patientId,
        doctorId,
        sessionId,
        visitNumber: visitCount + 1,
        status: 'active',
      });
      await consultation.save();

      // Setup Deepgram live connection
      const transcriptParts = [];
      const detectedLanguages = new Set();
      const segments = [];

      try {
        const dgConnection = createLiveTranscription({
          onOpen: () => {
            socket.emit('audio:ready', { sessionId, message: 'Recording started' });
          },
          onInterimTranscript: (result) => {
            socket.emit('transcript:interim', {
              text: result.text,
              language: result.language,
              confidence: result.confidence,
            });
          },
          onFinalTranscript: (result) => {
            transcriptParts.push(result.text);
            if (result.language && result.language !== 'unknown') {
              detectedLanguages.add(result.language);
            }

            // Track speaker segments
            segments.push({
              speaker: result.words?.[0]?.speaker !== undefined
                ? `speaker_${result.words[0].speaker}` : 'unknown',
              text: result.text,
              language: result.language,
              confidence: result.confidence,
              startTime: result.words?.[0]?.start || 0,
              endTime: result.words?.[result.words.length - 1]?.end || 0,
            });

            socket.emit('transcript:final', {
              text: result.text,
              language: result.language,
              confidence: result.confidence,
              fullTranscript: transcriptParts.join(' '),
            });
          },
          onError: (error) => {
            console.error('Deepgram error for session:', sessionId, error);
            socket.emit('error', { code: 'ASR_ERROR', message: 'Speech recognition error' });
          },
          onClose: () => {
            console.log(`🎤 Deepgram closed for session: ${sessionId}`);
          },
        });

        // Store session state
        activeSessions.set(socket.id, {
          sessionId,
          patientId,
          dgConnection,
          transcriptParts,
          detectedLanguages,
          segments,
          startTime: Date.now(),
        });

        console.log(`🎙️ Recording started: session=${sessionId}, patient=${patientId}`);
      } catch (error) {
        console.error('Failed to start Deepgram:', error);
        socket.emit('error', { code: 'ASR_INIT_ERROR', message: 'Failed to initialize speech recognition' });
        consultation.status = 'failed';
        await consultation.save();
      }
    });

    // ─── AUDIO CHUNK ───────────────────────────────────────
    socket.on('audio:chunk', (audioBuffer) => {
      const session = activeSessions.get(socket.id);
      if (!session || !session.dgConnection) {
        return;
      }

      // Forward audio to Deepgram
      const sent = sendAudioChunk(session.dgConnection, audioBuffer);
      if (!sent) {
        console.warn('⚠️ Failed to send audio chunk — connection not ready');
      }
    });

    // ─── STOP RECORDING ────────────────────────────────────
    socket.on('audio:stop', async () => {
      const session = activeSessions.get(socket.id);
      if (!session) {
        socket.emit('error', { code: 'NO_SESSION', message: 'No active recording session' });
        return;
      }

      const { sessionId, patientId, dgConnection, transcriptParts, detectedLanguages, segments, startTime } = session;

      // Close Deepgram connection
      closeLiveConnection(dgConnection);

      // Wait a moment for any final transcripts to arrive
      await new Promise((resolve) => setTimeout(resolve, 1500));

      const fullTranscript = transcriptParts.join(' ').trim();
      const languages = [...detectedLanguages];
      const durationSeconds = (Date.now() - startTime) / 1000;

      console.log(`📝 Full transcript (${durationSeconds.toFixed(1)}s): "${fullTranscript.substring(0, 100)}..."`);

      if (!fullTranscript) {
        socket.emit('extraction:complete', {
          sessionId,
          success: false,
          message: 'No speech detected in the recording.',
        });
        activeSessions.delete(socket.id);
        return;
      }

      // ─── LLM EXTRACTION ────────────────────────────────
      socket.emit('extraction:progress', { stage: 'Extracting medical data...', percent: 30 });

      try {
        // Step 1: Extract structured medical data
        const structuredData = await extractMedicalData(fullTranscript, languages);
        socket.emit('extraction:progress', { stage: 'Generating recommendations...', percent: 70 });

        // Step 2: Generate doctor assist suggestions
        let aiSuggestions = null;
        try {
          aiSuggestions = await generateDoctorAssist(structuredData);
        } catch (assistError) {
          console.warn('⚠️ Doctor assist failed:', assistError.message);
        }

        socket.emit('extraction:progress', { stage: 'Saving to database...', percent: 90 });

        // Step 3: Save to database
        await Consultation.findOneAndUpdate(
          { sessionId },
          {
            status: 'completed',
            rawTranscript: fullTranscript,
            detectedLanguages: languages,
            transcriptSegments: segments,
            structuredData,
            aiSuggestions: aiSuggestions || {},
            audioMetadata: {
              durationSeconds: Math.round(durationSeconds),
              format: 'linear16',
              sampleRate: 16000,
            },
          }
        );

        // Step 4: Send result to client
        socket.emit('extraction:complete', {
          sessionId,
          patientId,
          success: true,
          structuredData,
          aiSuggestions,
          transcript: fullTranscript,
          languages,
          duration: Math.round(durationSeconds),
        });

        console.log(`✅ Consultation completed: session=${sessionId}`);
      } catch (error) {
        console.error('❌ Extraction pipeline error:', error);

        // Still save the transcript even if extraction fails
        await Consultation.findOneAndUpdate(
          { sessionId },
          {
            status: 'failed',
            rawTranscript: fullTranscript,
            detectedLanguages: languages,
            transcriptSegments: segments,
          }
        );

        socket.emit('extraction:complete', {
          sessionId,
          success: false,
          message: `Extraction failed: ${error.message}`,
          transcript: fullTranscript,
        });
      }

      // Cleanup session
      activeSessions.delete(socket.id);
    });

    // ─── PROCESS UPLOADED AUDIO FILE ───────────────────────
    socket.on('audio:upload', async (data) => {
      const { patientId, audioBuffer, mimetype = 'audio/wav', doctorId = 'DOC-DEFAULT' } = data;

      if (!patientId || !audioBuffer) {
        socket.emit('error', { code: 'MISSING_DATA', message: 'Patient ID and audio data required' });
        return;
      }

      const sessionId = generateSessionId();
      const visitCount = await Consultation.countDocuments({ patientId });

      socket.emit('extraction:progress', { stage: 'Transcribing audio...', percent: 10 });

      try {
        const asrResult = await transcribeAudioFile(Buffer.from(audioBuffer), mimetype);
        
        console.log('\n================================================');
        console.log(`🗣️  DEEPGRAM TRANSCRIPT (File Upload):`);
        console.log(`"${asrResult.transcript}"`);
        console.log('================================================\n');

        socket.emit('extraction:progress', { stage: 'Extracting medical data...', percent: 40 });

        const structuredData = await extractMedicalData(
          asrResult.transcript,
          [asrResult.detectedLanguage]
        );

        socket.emit('extraction:progress', { stage: 'Generating recommendations...', percent: 70 });

        let aiSuggestions = null;
        try {
          aiSuggestions = await generateDoctorAssist(structuredData);
        } catch {}

        // Save consultation
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
          aiSuggestions: aiSuggestions || {},
          audioMetadata: {
            durationSeconds: Math.round(asrResult.metadata?.duration || 0),
            format: mimetype,
            sampleRate: 16000,
          },
        });
        await consultation.save();

        socket.emit('extraction:complete', {
          sessionId,
          patientId,
          success: true,
          structuredData,
          aiSuggestions,
          transcript: asrResult.transcript,
          languages: [asrResult.detectedLanguage],
          duration: Math.round(asrResult.metadata?.duration || 0),
        });
      } catch (error) {
        console.error('❌ Upload processing error:', error);
        socket.emit('extraction:complete', {
          sessionId,
          success: false,
          message: `Processing failed: ${error.message}`,
        });
      }
    });

    // ─── DISCONNECT ────────────────────────────────────────
    socket.on('disconnect', () => {
      const session = activeSessions.get(socket.id);
      if (session) {
        closeLiveConnection(session.dgConnection);
        activeSessions.delete(socket.id);
        console.log(`🔌 Client disconnected (cleaned up session): ${socket.id}`);
      } else {
        console.log(`🔌 Client disconnected: ${socket.id}`);
      }
    });
  });
};

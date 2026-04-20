import { useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import api from '../services/api';

export default function ConsultationPage() {
  const [searchParams] = useSearchParams();
  const [patientId, setPatientId] = useState(searchParams.get('patientId') || '');

  const [isRecording, setIsRecording] = useState(false);
  const [sessionId, setSessionId] = useState('');
  const [finalTranscript, setFinalTranscript] = useState('');
  const [statusMessage, setStatusMessage] = useState('');
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');

  const [manualTranscript, setManualTranscript] = useState('');
  const [manualBusy, setManualBusy] = useState(false);
  const [audioFile, setAudioFile] = useState(null);
  const [uploadBusy, setUploadBusy] = useState(false);
  const [recordingBusy, setRecordingBusy] = useState(false);

  const mediaRecorderRef = useRef(null);
  const mediaStreamRef = useRef(null);
  const recordingChunksRef = useRef([]);
  const fileInputRef = useRef(null);

  const processAudioFile = async (file) => {
    if (!patientId.trim()) {
      setError('Patient ID is required');
      return;
    }

    if (!file) {
      setError('Please select or record an audio file');
      return;
    }

    setUploadBusy(true);
    setError('');
    setResult(null);
    setStatusMessage('Uploading audio and processing consultation...');

    try {
      const formData = new FormData();
      formData.append('patientId', patientId.trim());
      formData.append('audio', file);

      const { data } = await api.post('/consultations', formData, {
        timeout: 180000,
      });

      setResult({
        success: true,
        sessionId: data.consultation.sessionId,
        structuredData: data.consultation.structuredData,
        aiSuggestions: data.consultation.aiSuggestions,
        transcript: data.consultation.transcript,
      });
      setSessionId(data.consultation.sessionId);
      setFinalTranscript(data.consultation.transcript || '');
      setStatusMessage('Consultation processed successfully.');
    } catch (uploadError) {
      const serverError = uploadError?.response?.data?.error;
      const statusCode = uploadError?.response?.status;
      setError(
        serverError
          ? `Upload failed (${statusCode}): ${serverError}`
          : (uploadError.message || 'Failed to process audio upload')
      );
      setStatusMessage('');
    } finally {
      setUploadBusy(false);
    }
  };

  const startRecording = async () => {
    if (!patientId.trim()) {
      setError('Patient ID is required');
      return;
    }

    setError('');
    setResult(null);
    setFinalTranscript('');
    setStatusMessage('Recording in progress...');
    recordingChunksRef.current = [];

    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      mediaStreamRef.current = stream;

      const mediaRecorder = new MediaRecorder(stream, {
        mimeType: MediaRecorder.isTypeSupported('audio/webm') ? 'audio/webm' : undefined,
      });

      mediaRecorderRef.current = mediaRecorder;

      mediaRecorder.ondataavailable = (event) => {
        if (event.data && event.data.size > 0) {
          recordingChunksRef.current.push(event.data);
        }
      };

      mediaRecorder.onstop = async () => {
        mediaStreamRef.current?.getTracks()?.forEach((track) => track.stop());
        mediaRecorderRef.current = null;
        mediaStreamRef.current = null;

        if (!recordingChunksRef.current.length) {
          setStatusMessage('No audio recorded. Please try again.');
          return;
        }

        const blob = new Blob(recordingChunksRef.current, { type: 'audio/webm' });
        const recordedFile = new File([blob], `recording-${Date.now()}.webm`, { type: 'audio/webm' });
        setAudioFile(recordedFile);
        if (fileInputRef.current) fileInputRef.current.value = '';

        setRecordingBusy(true);
        setStatusMessage('Recording complete. Processing audio...');
        await processAudioFile(recordedFile);
        setRecordingBusy(false);
      };

      mediaRecorder.start(1000);
      setIsRecording(true);
    } catch (startError) {
      setError(startError.message || 'Could not access microphone');
      setStatusMessage('');
    }
  };

  const stopRecording = () => {
    if (!isRecording) return;

    mediaRecorderRef.current?.stop();
    setIsRecording(false);
  };

  const submitManualTranscript = async () => {
    if (!patientId.trim() || !manualTranscript.trim()) {
      setError('Patient ID and transcript are required');
      return;
    }

    setManualBusy(true);
    setError('');

    try {
      const { data } = await api.post('/consultations?assist=true', {
        patientId,
        transcript: manualTranscript,
      });
      setResult({
        success: true,
        sessionId: data.consultation.sessionId,
        structuredData: data.consultation.structuredData,
        aiSuggestions: data.consultation.aiSuggestions,
        transcript: data.consultation.transcript,
      });
      setSessionId(data.consultation.sessionId);
      setFinalTranscript(data.consultation.transcript || '');
      setStatusMessage('Consultation processed successfully.');
    } catch (manualError) {
      setError(manualError?.response?.data?.error || manualError.message || 'Failed to process transcript');
      setStatusMessage('');
    } finally {
      setManualBusy(false);
    }
  };

  const uploadVoiceFile = async () => {
    await processAudioFile(audioFile);
    setAudioFile(null);
    if (fileInputRef.current) fileInputRef.current.value = '';
  };

  return (
    <div className="space-y-6">
      <section className="rounded-2xl border border-slate-700 bg-slate-900/70 p-5">
        <h1 className="text-2xl font-bold">Voice Consultation</h1>
        <p className="text-sm text-slate-300">Record voice locally, then send the recording to consultation API for processing.</p>

        <div className="mt-4 flex flex-wrap gap-3">
          <input
            value={patientId}
            onChange={(e) => setPatientId(e.target.value)}
            placeholder="Patient ID (e.g. PAT-123456)"
            className="min-w-[280px] rounded-lg border border-slate-700 bg-slate-950 px-3 py-2"
          />
          {!isRecording ? (
            <button
              type="button"
              onClick={startRecording}
              disabled={recordingBusy || uploadBusy}
              className="rounded-lg bg-rose-300 px-4 py-2 font-semibold text-slate-900 disabled:opacity-60"
            >
              {recordingBusy ? 'Processing...' : 'Record & Process'}
            </button>
          ) : (
            <button type="button" onClick={stopRecording} className="rounded-lg bg-amber-300 px-4 py-2 font-semibold text-slate-900">
              Stop Recording
            </button>
          )}
        </div>

        {statusMessage && <p className="mt-4 rounded-xl border border-cyan-500/50 bg-cyan-500/10 p-3 text-sm text-cyan-100">{statusMessage}</p>}

        {error && <p className="mt-4 rounded-xl border border-rose-500/40 bg-rose-500/10 p-3 text-sm text-rose-200">{error}</p>}

        <div className="mt-4 text-xs text-slate-400">Session: {sessionId || 'not started'}</div>
        <div className="mt-4 rounded-xl border border-slate-700 bg-slate-950 p-3">
          <p className="mb-2 text-xs uppercase tracking-[0.2em] text-slate-400">Transcript</p>
          <p className="text-sm text-slate-200">{finalTranscript || 'Processed transcript will appear here.'}</p>
        </div>
      </section>

      <section className="rounded-2xl border border-slate-700 bg-slate-900/70 p-5">
        <h2 className="text-lg font-semibold">Manual Transcript Processing</h2>
        <p className="text-sm text-slate-300">Paste transcript and process through consultation API if microphone streaming is unavailable.</p>
        <textarea
          className="mt-3 h-36 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2"
          value={manualTranscript}
          onChange={(e) => setManualTranscript(e.target.value)}
          placeholder="Paste doctor-patient transcript..."
        />
        <button type="button" onClick={submitManualTranscript} disabled={manualBusy} className="mt-3 rounded-lg bg-emerald-300 px-4 py-2 font-semibold text-slate-900 disabled:opacity-60">
          {manualBusy ? 'Processing...' : 'Process Transcript'}
        </button>
      </section>

      <section className="rounded-2xl border border-slate-700 bg-slate-900/70 p-5">
        <h2 className="text-lg font-semibold">Upload Voice File</h2>
        <p className="text-sm text-slate-300">Upload an audio file to process through the same consultation route (supports WAV, WebM, OGG, MP3, M4A, MP4).</p>

        <div className="mt-3 flex flex-col gap-3 md:flex-row md:items-center">
          <input
            ref={fileInputRef}
            type="file"
            accept="audio/*,.wav,.webm,.ogg,.mp3,.m4a,.mp4"
            onChange={(e) => setAudioFile(e.target.files?.[0] || null)}
            className="block w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm file:mr-4 file:rounded-md file:border-0 file:bg-slate-800 file:px-3 file:py-2 file:text-slate-100"
          />
          <button
            type="button"
            onClick={uploadVoiceFile}
            disabled={uploadBusy}
            className="rounded-lg bg-cyan-300 px-4 py-2 font-semibold text-slate-900 disabled:opacity-60"
          >
            {uploadBusy ? 'Uploading...' : 'Upload & Process'}
          </button>
        </div>

        {audioFile && (
          <p className="mt-3 text-xs text-slate-400">
            Selected: {audioFile.name} ({Math.ceil(audioFile.size / 1024)} KB)
          </p>
        )}
      </section>

      <section className="rounded-2xl border border-slate-700 bg-slate-900/70 p-5">
        <h2 className="text-lg font-semibold">Extraction Result</h2>
        {result ? (
          <div className="mt-3 space-y-3 text-sm">
            <p><span className="text-slate-400">Success:</span> {String(result.success)}</p>
            <p><span className="text-slate-400">Session ID:</span> {result.sessionId}</p>
            <pre className="overflow-auto rounded-xl border border-slate-700 bg-slate-950 p-3 text-xs text-slate-200">
{JSON.stringify(result.structuredData || {}, null, 2)}
            </pre>
            <pre className="overflow-auto rounded-xl border border-slate-700 bg-slate-950 p-3 text-xs text-slate-200">
{JSON.stringify(result.aiSuggestions || {}, null, 2)}
            </pre>
          </div>
        ) : (
          <p className="mt-3 text-sm text-slate-300">No result yet.</p>
        )}
      </section>
    </div>
  );
}

import { useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import api from '../services/api';
import { Mic, Upload, FileText, LoaderCircle, Sparkles, ArrowRight, Download } from 'lucide-react';

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
  const [downloadingReport, setDownloadingReport] = useState(false);

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

  const downloadReport = async () => {
    if (!patientId.trim()) {
      setError('Patient ID is required');
      return;
    }

    setDownloadingReport(true);
    setError('');

    try {
      const { data } = await api.get(`/patients/${patientId}/report`, { responseType: 'blob' });
      const blob = new Blob([data], { type: 'application/pdf' });
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `${patientId}-report.pdf`;
      link.click();
      URL.revokeObjectURL(url);
    } catch (downloadError) {
      setError(downloadError?.response?.data?.error || downloadError.message || 'Failed to download report');
    } finally {
      setDownloadingReport(false);
    }
  };

  const structuredData = result?.structuredData || {};
  const suggestions = result?.aiSuggestions || {};
  const symptoms = Array.isArray(structuredData.symptoms) ? structuredData.symptoms : [];
  const diagnoses = Array.isArray(structuredData.diagnosis) ? structuredData.diagnosis : [];
  const medications = Array.isArray(structuredData.medications) ? structuredData.medications : [];
  const allergies = Array.isArray(structuredData.allergies) ? structuredData.allergies : [];
  const flaggedIssues = Array.isArray(structuredData.flaggedIssues) ? structuredData.flaggedIssues : [];
  const missingInfo = Array.isArray(structuredData.missingInfo) ? structuredData.missingInfo : [];

  const possibleDiagnoses = Array.isArray(suggestions.possibleDiagnoses) ? suggestions.possibleDiagnoses : [];
  const recommendedTests = Array.isArray(suggestions.recommendedTests) ? suggestions.recommendedTests : [];
  const warnings = Array.isArray(suggestions.warnings) ? suggestions.warnings : [];
  const drugInteractions = Array.isArray(suggestions.drugInteractions) ? suggestions.drugInteractions : [];

  return (
    <div className="space-y-6">
      <section className="surface rounded-[2rem] p-6 md:p-8">
        <div className="max-w-3xl">
          <p className="text-xs font-semibold uppercase tracking-[0.3em] text-slate-500">Consultation</p>
          <h1 className="mt-3 text-3xl font-bold tracking-tight text-slate-900 md:text-4xl">Record, upload, or paste a transcript</h1>
          <p className="mt-3 text-sm leading-relaxed text-slate-600">
            This workflow keeps voice capture local and sends the final audio or transcript to the consultation API.
          </p>
        </div>

        <div className="mt-6 grid gap-4 lg:grid-cols-[1fr_auto] lg:items-center">
          <input
            value={patientId}
            onChange={(e) => setPatientId(e.target.value)}
            placeholder="Patient ID, for example PAT-123456"
            className="clean-input"
          />
          <div className="flex flex-wrap gap-3">
            {!isRecording ? (
              <button
                type="button"
                onClick={startRecording}
                disabled={recordingBusy || uploadBusy}
                className="clean-button bg-slate-900 px-5 py-3 text-white disabled:opacity-60"
              >
                <Mic size={16} /> {recordingBusy ? 'Processing...' : 'Record & process'}
              </button>
            ) : (
              <button type="button" onClick={stopRecording} className="clean-button bg-amber-400 px-5 py-3 text-slate-900">
                <LoaderCircle size={16} className="animate-spin" /> Stop recording
              </button>
            )}
            <button
              type="button"
              onClick={downloadReport}
              disabled={downloadingReport || !patientId.trim()}
              className="clean-button border border-slate-200 bg-white px-5 py-3 text-slate-700 disabled:opacity-60"
            >
              <Download size={16} /> {downloadingReport ? 'Preparing report...' : 'Download report'}
            </button>
          </div>
        </div>

        <div className="mt-6 grid gap-3 md:grid-cols-3">
          <div className="rounded-2xl border border-slate-200 bg-white p-4">
            <p className="text-xs font-semibold uppercase tracking-[0.2em] text-slate-500">Status</p>
            <p className="mt-2 text-sm text-slate-700">{statusMessage || 'Ready to record or upload.'}</p>
          </div>
          <div className="rounded-2xl border border-slate-200 bg-white p-4">
            <p className="text-xs font-semibold uppercase tracking-[0.2em] text-slate-500">Session</p>
            <p className="mt-2 text-sm text-slate-700">{sessionId || 'Not started'}</p>
          </div>
          <div className="rounded-2xl border border-slate-200 bg-white p-4">
            <p className="text-xs font-semibold uppercase tracking-[0.2em] text-slate-500">Transcript</p>
            <p className="mt-2 line-clamp-2 text-sm text-slate-700">{finalTranscript || 'Processed transcript will appear here.'}</p>
          </div>
        </div>

        {error && <p className="mt-4 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">{error}</p>}
      </section>

      <section className="grid gap-6 lg:grid-cols-2">
        <div className="surface rounded-[2rem] p-6">
          <div className="mb-4 flex items-center gap-2 text-slate-900">
            <FileText size={18} className="text-slate-500" />
            <h2 className="text-lg font-semibold">Manual transcript</h2>
          </div>
          <p className="text-sm text-slate-500">Paste a transcript when microphone access is not available.</p>
          <textarea
            className="clean-input mt-4 min-h-40 resize-none"
            value={manualTranscript}
            onChange={(e) => setManualTranscript(e.target.value)}
            placeholder="Paste doctor-patient transcript..."
          />
          <button type="button" onClick={submitManualTranscript} disabled={manualBusy} className="clean-button mt-4 bg-slate-900 px-5 py-3 text-white disabled:opacity-60">
            {manualBusy ? 'Processing...' : 'Process transcript'} <ArrowRight size={16} />
          </button>
        </div>

        <div className="surface rounded-[2rem] p-6">
          <div className="mb-4 flex items-center gap-2 text-slate-900">
            <Upload size={18} className="text-slate-500" />
            <h2 className="text-lg font-semibold">Voice file upload</h2>
          </div>
          <p className="text-sm text-slate-500">Upload an audio file and use the same consultation route for processing.</p>

          <input
            ref={fileInputRef}
            type="file"
            accept="audio/*,.wav,.webm,.ogg,.mp3,.m4a,.mp4"
            onChange={(e) => setAudioFile(e.target.files?.[0] || null)}
            className="clean-input mt-4 file:mr-4 file:rounded-full file:border-0 file:bg-slate-900 file:px-4 file:py-2 file:text-sm file:text-white"
          />

          <button
            type="button"
            onClick={uploadVoiceFile}
            disabled={uploadBusy || !audioFile}
            className="clean-button mt-4 bg-slate-900 px-5 py-3 text-white disabled:opacity-60"
          >
            {uploadBusy ? 'Uploading...' : 'Upload & process'} <Sparkles size={16} />
          </button>

          {audioFile && (
            <p className="mt-3 text-xs text-slate-500">
              Selected: {audioFile.name} ({Math.ceil(audioFile.size / 1024)} KB)
            </p>
          )}
        </div>
      </section>

      <section className="surface rounded-[2rem] p-6">
        <h2 className="text-lg font-semibold text-slate-900">Extraction result</h2>
        {result ? (
          <div className="mt-4 space-y-4 text-sm">
            <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
              <div className="rounded-2xl border border-slate-200 bg-white p-4">
                <p className="text-xs uppercase tracking-[0.2em] text-slate-500">Success</p>
                <p className="mt-2 text-sm font-semibold text-slate-900">{String(result.success)}</p>
              </div>
              <div className="rounded-2xl border border-slate-200 bg-white p-4">
                <p className="text-xs uppercase tracking-[0.2em] text-slate-500">Session ID</p>
                <p className="mt-2 text-sm font-semibold text-slate-900">{result.sessionId}</p>
              </div>
            </div>

            <div className="grid gap-4 lg:grid-cols-2">
              <div className="space-y-4">
                <div>
                  <p className="mb-2 text-xs font-semibold uppercase tracking-[0.2em] text-slate-500">Chief complaint</p>
                  <p className="rounded-2xl border border-slate-200 bg-white p-4 text-slate-700">
                    {structuredData.chiefComplaint || 'Not available'}
                  </p>
                </div>

                <div>
                  <p className="mb-2 text-xs font-semibold uppercase tracking-[0.2em] text-slate-500">Symptoms</p>
                  {symptoms.length > 0 ? (
                    <div className="space-y-2 rounded-2xl border border-slate-200 bg-white p-4">
                      {symptoms.map((symptom, index) => (
                        <div key={index} className="rounded-xl border border-slate-100 bg-slate-50 p-3 text-slate-700">
                          <p className="font-semibold text-slate-900">{symptom.name || 'Unnamed symptom'}</p>
                          <p className="mt-1 text-xs text-slate-600">
                            Duration: {symptom.duration || 'N/A'} • Severity: {symptom.severity || 'N/A'}
                          </p>
                          {symptom.notes ? <p className="mt-1 text-xs text-slate-600">Notes: {symptom.notes}</p> : null}
                        </div>
                      ))}
                    </div>
                  ) : (
                    <p className="rounded-2xl border border-slate-200 bg-white p-4 text-slate-600">No symptoms extracted.</p>
                  )}
                </div>

                <div>
                  <p className="mb-2 text-xs font-semibold uppercase tracking-[0.2em] text-slate-500">Diagnosis</p>
                  {diagnoses.length > 0 ? (
                    <ul className="space-y-2 rounded-2xl border border-slate-200 bg-white p-4 text-slate-700">
                      {diagnoses.map((diagnosis, index) => (
                        <li key={index} className="list-inside list-disc">{diagnosis}</li>
                      ))}
                    </ul>
                  ) : (
                    <p className="rounded-2xl border border-slate-200 bg-white p-4 text-slate-600">No diagnosis extracted.</p>
                  )}
                </div>

                <div>
                  <p className="mb-2 text-xs font-semibold uppercase tracking-[0.2em] text-slate-500">Medications</p>
                  {medications.length > 0 ? (
                    <div className="space-y-2 rounded-2xl border border-slate-200 bg-white p-4">
                      {medications.map((medication, index) => (
                        <div key={index} className="rounded-xl border border-slate-100 bg-slate-50 p-3 text-slate-700">
                          <p className="font-semibold text-slate-900">{medication.name || 'Unnamed medication'}</p>
                          <p className="mt-1 text-xs text-slate-600">
                            {medication.dosage || 'N/A'} • {medication.frequency || 'N/A'} • {medication.duration || 'N/A'}
                          </p>
                          {medication.notes ? <p className="mt-1 text-xs text-slate-600">Notes: {medication.notes}</p> : null}
                        </div>
                      ))}
                    </div>
                  ) : (
                    <p className="rounded-2xl border border-slate-200 bg-white p-4 text-slate-600">No medications extracted.</p>
                  )}
                </div>
              </div>

              <div className="space-y-4">
                <div>
                  <p className="mb-2 text-xs font-semibold uppercase tracking-[0.2em] text-slate-500">Vitals and Follow-up</p>
                  <div className="rounded-2xl border border-slate-200 bg-white p-4 text-slate-700">
                    <div className="grid gap-2 sm:grid-cols-2">
                      {Object.entries(structuredData.vitals || {}).map(([key, value]) => (
                        <div key={key} className="rounded-xl border border-slate-100 bg-slate-50 px-3 py-2 text-xs">
                          <span className="font-semibold uppercase tracking-[0.12em] text-slate-500">{key}</span>
                          <p className="mt-1 text-sm text-slate-800">{value || 'N/A'}</p>
                        </div>
                      ))}
                    </div>
                    <p className="mt-3 text-sm"><span className="font-semibold text-slate-900">Follow-up:</span> {structuredData.followUp || 'N/A'}</p>
                    {structuredData.additionalNotes ? (
                      <p className="mt-2 text-sm"><span className="font-semibold text-slate-900">Additional notes:</span> {structuredData.additionalNotes}</p>
                    ) : null}
                  </div>
                </div>

                <div>
                  <p className="mb-2 text-xs font-semibold uppercase tracking-[0.2em] text-slate-500">Allergies, Flags, Missing Info</p>
                  <div className="space-y-2 rounded-2xl border border-slate-200 bg-white p-4 text-slate-700">
                    <p><span className="font-semibold text-slate-900">Allergies:</span> {allergies.length ? allergies.join(', ') : 'None reported'}</p>
                    <p><span className="font-semibold text-slate-900">Flags:</span> {flaggedIssues.length ? flaggedIssues.join(' | ') : 'None'}</p>
                    <p><span className="font-semibold text-slate-900">Missing info:</span> {missingInfo.length ? missingInfo.join(' | ') : 'None'}</p>
                  </div>
                </div>

                <div>
                  <p className="mb-2 text-xs font-semibold uppercase tracking-[0.2em] text-slate-500">Doctor assist suggestions</p>
                  <div className="space-y-2 rounded-2xl border border-slate-200 bg-white p-4 text-slate-700">
                    <p className="font-semibold text-slate-900">Possible diagnoses</p>
                    {possibleDiagnoses.length ? (
                      <ul className="space-y-2">
                        {possibleDiagnoses.map((item, index) => (
                          <li key={index} className="rounded-xl border border-slate-100 bg-slate-50 p-3 text-xs">
                            <p className="text-sm font-semibold text-slate-900">{item.condition || 'Unknown condition'} ({item.confidence || 'N/A'})</p>
                            <p className="mt-1 text-slate-600">{item.reasoning || 'No reasoning provided.'}</p>
                          </li>
                        ))}
                      </ul>
                    ) : (
                      <p className="text-sm text-slate-600">No suggested diagnoses.</p>
                    )}

                    <p className="pt-2 font-semibold text-slate-900">Recommended tests</p>
                    {recommendedTests.length ? (
                      <ul className="space-y-1 text-sm">
                        {recommendedTests.map((test, index) => (
                          <li key={index} className="list-inside list-disc">
                            {test.test || 'Unnamed test'} {test.urgency ? `(${test.urgency})` : ''} {test.reason ? `- ${test.reason}` : ''}
                          </li>
                        ))}
                      </ul>
                    ) : (
                      <p className="text-sm text-slate-600">No tests suggested.</p>
                    )}

                    <p className="pt-2 font-semibold text-slate-900">Warnings</p>
                    <p className="text-sm">{warnings.length ? warnings.join(' | ') : 'None'}</p>

                    <p className="pt-2 font-semibold text-slate-900">Drug interactions</p>
                    {drugInteractions.length ? (
                      <ul className="space-y-1 text-sm">
                        {drugInteractions.map((item, index) => (
                          <li key={index} className="list-inside list-disc">
                            {item.drug1 || 'Drug A'} + {item.drug2 || 'Drug B'}: {item.severity || 'N/A'} {item.effect ? `- ${item.effect}` : ''}
                          </li>
                        ))}
                      </ul>
                    ) : (
                      <p className="text-sm text-slate-600">No interaction warnings.</p>
                    )}
                  </div>
                </div>

                <details className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
                  <summary className="cursor-pointer text-xs font-semibold uppercase tracking-[0.2em] text-slate-500">Raw JSON (debug view)</summary>
                  <div className="mt-3 grid gap-3">
                    <pre className="max-h-44 overflow-auto whitespace-pre-wrap break-words rounded-xl border border-slate-200 bg-white p-3 text-[11px] leading-relaxed text-slate-700">{JSON.stringify(structuredData, null, 2)}</pre>
                    <pre className="max-h-44 overflow-auto whitespace-pre-wrap break-words rounded-xl border border-slate-200 bg-white p-3 text-[11px] leading-relaxed text-slate-700">{JSON.stringify(suggestions, null, 2)}</pre>
                  </div>
                </details>
              </div>
            </div>
          </div>
        ) : (
          <p className="mt-4 text-sm text-slate-500">No result yet.</p>
        )}
      </section>
    </div>
  );
}

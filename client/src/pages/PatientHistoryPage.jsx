import { useEffect, useRef, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import api from '../services/api';
import { ChevronLeft, AlertCircle, FileText, ShieldAlert, Sparkles, Clock, ArrowRight, Download, Play, Pause } from 'lucide-react';

export default function PatientHistoryPage() {
  const { patientId } = useParams();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [patient, setPatient] = useState(null);
  const [consultations, setConsultations] = useState([]);
  const [downloadingReport, setDownloadingReport] = useState(false);
  const [downloadingEncounter, setDownloadingEncounter] = useState('');
  const [playingAudio, setPlayingAudio] = useState('');
  const utteranceRef = useRef(null);

  useEffect(() => {
    const fetchHistory = async () => {
      try {
        const { data } = await api.get(`/patients/${patientId}/history`);
        setPatient(data.patient);
        setConsultations(data.consultations || []);
      } catch (err) {
        setError(err?.response?.data?.error || err.message || 'Failed to load patient history');
      } finally {
        setLoading(false);
      }
    };

    fetchHistory();
  }, [patientId]);

  // Cleanup speech synthesis on unmount
  useEffect(() => {
    return () => {
      if ('speechSynthesis' in window) {
        window.speechSynthesis.cancel();
      }
    };
  }, []);

  if (loading) {
    return (
      <div className="flex min-h-[60vh] items-center justify-center">
        <div className="surface rounded-[2rem] px-6 py-5 text-slate-600">
          Loading patient history...
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="mx-auto mt-10 max-w-2xl px-4">
        <div className="surface rounded-[2rem] p-8 text-center">
          <AlertCircle size={40} className="mx-auto text-rose-500" />
          <h2 className="mt-4 text-2xl font-bold text-slate-900">Could not load patient history</h2>
          <p className="mt-2 text-sm text-slate-600">{error}</p>
          <Link to="/" className="clean-button mt-6 bg-slate-900 px-5 py-3 text-white">
            <ChevronLeft size={16} /> Return to dashboard
          </Link>
        </div>
      </div>
    );
  }

  if (!patient) return null;

  const downloadReport = async () => {
    setDownloadingReport(true);
    try {
      const { data } = await api.get(`/patients/${patientId}/report`, { responseType: 'blob' });
      const pdfBlob = new Blob([data], { type: 'application/pdf' });
      const textUrl = URL.createObjectURL(pdfBlob);
      const textLink = document.createElement('a');
      textLink.href = textUrl;
      textLink.download = `${patientId}-report.pdf`;
      textLink.click();
      URL.revokeObjectURL(textUrl);
    } finally {
      setDownloadingReport(false);
    }
  };

  const downloadEncounterReport = async (sessionId) => {
    setDownloadingEncounter(sessionId);
    try {
      const { data } = await api.get(`/consultations/${sessionId}/report`, { responseType: 'blob' });
      const pdfBlob = new Blob([data], { type: 'application/pdf' });
      const textUrl = URL.createObjectURL(pdfBlob);
      const textLink = document.createElement('a');
      textLink.href = textUrl;
      textLink.download = `${sessionId}-encounter-report.pdf`;
      textLink.click();
      URL.revokeObjectURL(textUrl);
    } finally {
      setDownloadingEncounter('');
    }
  };

  const toggleAudioPlayback = async (sessionId) => {
    if (!('speechSynthesis' in window)) {
      alert('Text-to-speech is not supported in this browser.');
      return;
    }

    if (playingAudio === sessionId) {
      window.speechSynthesis.cancel();
      setPlayingAudio('');
    } else {
      try {
        window.speechSynthesis.cancel();

        setPlayingAudio(`${sessionId}-loading`);

        // Fetch encounter report text and read it aloud.
        const response = await api.get(`/consultations/${sessionId}/report?format=json`);
        const text = (response?.data?.report?.text || '').toString().trim();
        if (!text) {
          throw new Error('No report text available to read aloud');
        }

        const utterance = new SpeechSynthesisUtterance(text.slice(0, 3500));
        utterance.rate = 1;
        utterance.pitch = 1;
        utterance.volume = 1;
        utterance.lang = 'en-US';

        utterance.onend = () => {
          setPlayingAudio('');
        };
        utterance.onerror = () => {
          alert('Failed to read this encounter aloud.');
          setPlayingAudio('');
        };

        utteranceRef.current = utterance;
        setPlayingAudio(sessionId);
        window.speechSynthesis.speak(utterance);
      } catch (error) {
        const errorMsg = error?.response?.data?.error || error?.message || 'Failed to prepare speech';
        alert(`Audio error: ${errorMsg}`);
        setPlayingAudio('');
      }
    }
  };

  return (
    <div className="space-y-6 pb-12">
      <section className="surface rounded-[2rem] p-6 md:p-8">
        <div className="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.3em] text-slate-500">Patient history</p>
            <h1 className="mt-3 text-3xl font-bold tracking-tight text-slate-900">{patient.name}</h1>
            <p className="mt-2 text-sm text-slate-500">
              {patient.age ? `${patient.age} years old` : 'Age not recorded'} • {patient.gender || 'Gender not recorded'} • {patientId}
            </p>
          </div>

          <div className="flex flex-col gap-3 sm:flex-row">
            <button type="button" onClick={downloadReport} disabled={downloadingReport} className="clean-button border border-slate-200 bg-white px-5 py-3 text-slate-700 disabled:opacity-60">
              <Download size={16} /> {downloadingReport ? 'Preparing report...' : 'Download report'}
            </button>
            <Link to={`/consultation?patientId=${patientId}`} className="clean-button bg-slate-900 px-5 py-3 text-white">
              <Sparkles size={16} /> Start consultation
            </Link>
          </div>
        </div>

        <div className="mt-6 grid gap-3 md:grid-cols-3">
          <div className="rounded-2xl border border-slate-200 bg-white p-4">
            <p className="text-xs font-semibold uppercase tracking-[0.2em] text-slate-500">Blood group</p>
            <p className="mt-2 text-lg font-semibold text-slate-900">{patient.bloodGroup || 'N/A'}</p>
          </div>
          <div className="rounded-2xl border border-slate-200 bg-white p-4">
            <p className="text-xs font-semibold uppercase tracking-[0.2em] text-slate-500">Contact</p>
            <p className="mt-2 text-lg font-semibold text-slate-900">{patient.phone || 'N/A'}</p>
          </div>
          <div className="rounded-2xl border border-slate-200 bg-white p-4">
            <p className="text-xs font-semibold uppercase tracking-[0.2em] text-slate-500">Encounters</p>
            <p className="mt-2 text-lg font-semibold text-slate-900">{consultations.length}</p>
          </div>
        </div>

        <div className="mt-4 rounded-2xl border border-slate-200 bg-slate-50 p-4">
          <div className="mb-2 flex items-center gap-2 text-sm font-semibold text-slate-900">
            <ShieldAlert size={16} className="text-amber-600" /> Allergies
          </div>
          {patient.allergies && patient.allergies.length > 0 ? (
            <div className="flex flex-wrap gap-2">
              {patient.allergies.map((allergy, index) => (
                <span key={index} className="rounded-full border border-amber-200 bg-amber-50 px-3 py-1 text-sm text-amber-800">
                  {allergy}
                </span>
              ))}
            </div>
          ) : (
            <p className="text-sm text-slate-500">No known allergies</p>
          )}
        </div>
      </section>

      <section className="surface rounded-[2rem] p-6 md:p-8">
        <div className="flex items-center gap-2 text-slate-900">
          <Clock size={18} className="text-slate-500" />
          <h2 className="text-xl font-semibold">Clinical timeline</h2>
        </div>

        {consultations.length === 0 ? (
          <div className="mt-6 rounded-3xl border border-dashed border-slate-200 bg-slate-50 p-10 text-center">
            <FileText size={40} className="mx-auto text-slate-300" />
            <p className="mt-4 text-lg font-semibold text-slate-900">No consultations yet</p>
            <p className="mt-2 text-sm text-slate-500">Start the first consultation to create the patient record timeline.</p>
            <Link to={`/consultation?patientId=${patientId}`} className="clean-button mt-6 bg-slate-900 px-5 py-3 text-white">
              <ArrowRight size={16} /> Start first consultation
            </Link>
          </div>
        ) : (
          <div className="mt-6 space-y-4">
            {consultations.map((consult) => {
              const dateObj = new Date(consult.consultationDate);
              const data = consult.structuredData || {};
              const followUp = data.followUp || data.followUpPlan || '';

              return (
                <article key={consult._id} className="rounded-3xl border border-slate-200 bg-white p-5 md:p-6">
                  <div className="flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
                    <div>
                      <p className="text-sm font-semibold text-slate-900">
                        Encounter #{consult.visitNumber} <span className="font-normal text-slate-500">• {dateObj.toLocaleString('en-US', { weekday: 'short', month: 'short', day: 'numeric', year: 'numeric' })}</span>
                      </p>
                      <p className="mt-1 text-xs text-slate-500">
                        {dateObj.toLocaleString('en-US', { hour: 'numeric', minute: '2-digit' })} • Session {consult.sessionId?.slice(0, 8)}
                      </p>
                    </div>
                    <div className="text-sm text-slate-500">Doctor: {consult.doctorId || 'Default'}</div>
                  </div>

                  <div className="mt-5 flex gap-3 justify-end">
                    <button
                      type="button"
                      onClick={() => toggleAudioPlayback(consult.sessionId)}
                      disabled={playingAudio === `${consult.sessionId}-loading`}
                      className={`clean-button flex items-center gap-2 px-4 py-2 ${
                        playingAudio === consult.sessionId
                          ? 'bg-blue-100 border border-blue-300 text-blue-700'
                          : 'border border-slate-200 bg-white text-slate-700'
                      }`}
                    >
                      {playingAudio === `${consult.sessionId}-loading` ? (
                        <>
                          <div className="animate-spin h-4 w-4 border-2 border-blue-300 border-t-blue-600 rounded-full"></div>
                          Generating audio...
                        </>
                      ) : playingAudio === consult.sessionId ? (
                        <>
                          <Pause size={16} /> Stop audio
                        </>
                      ) : (
                        <>
                          <Play size={16} /> Listen
                        </>
                      )}
                    </button>
                    <button
                      type="button"
                      onClick={() => downloadEncounterReport(consult.sessionId)}
                      disabled={downloadingEncounter === consult.sessionId}
                      className="clean-button border border-slate-200 bg-white px-4 py-2 text-slate-700 disabled:opacity-60"
                    >
                      <Download size={16} />
                      {downloadingEncounter === consult.sessionId ? 'Preparing...' : 'Download report'}
                    </button>
                  </div>

                  <div className="mt-5 grid gap-4 lg:grid-cols-2">
                    <div className="space-y-4">
                      {(data.chiefComplaint || (data.symptoms && data.symptoms.length > 0)) && (
                        <div>
                          <p className="text-xs font-semibold uppercase tracking-[0.2em] text-slate-500">Chief complaint and symptoms</p>
                          <div className="mt-2 rounded-2xl border border-slate-200 bg-slate-50 p-4">
                            {data.chiefComplaint && <p className="text-sm text-slate-700">{data.chiefComplaint}</p>}
                            {data.symptoms && data.symptoms.length > 0 && (
                              <div className="mt-3 flex flex-wrap gap-2">
                                {data.symptoms.map((symptom, index) => (
                                  <span key={index} className="rounded-full border border-slate-200 bg-white px-3 py-1 text-xs text-slate-700">
                                    {symptom.name}{symptom.severity ? ` (${symptom.severity})` : ''}
                                  </span>
                                ))}
                              </div>
                            )}
                          </div>
                        </div>
                      )}

                      {data.vitals && Object.keys(data.vitals).length > 0 && (
                        <div>
                          <p className="text-xs font-semibold uppercase tracking-[0.2em] text-slate-500">Vitals</p>
                          <div className="mt-2 grid gap-3 sm:grid-cols-2">
                            {Object.entries(data.vitals).map(([key, value]) =>
                              value ? (
                                <div key={key} className="rounded-2xl border border-slate-200 bg-white p-4">
                                  <p className="text-xs uppercase tracking-[0.2em] text-slate-500">{key}</p>
                                  <p className="mt-2 text-sm font-semibold text-slate-900">{value}</p>
                                </div>
                              ) : null
                            )}
                          </div>
                        </div>
                      )}
                    </div>

                    <div className="space-y-4">
                      {data.diagnosis && data.diagnosis.length > 0 && (
                        <div>
                          <p className="text-xs font-semibold uppercase tracking-[0.2em] text-slate-500">Assessment</p>
                          <ul className="mt-2 space-y-2 rounded-2xl border border-slate-200 bg-white p-4 text-sm text-slate-700">
                            {data.diagnosis.map((item, index) => (
                              <li key={index} className="flex items-start gap-2">
                                <span className="mt-2 h-1.5 w-1.5 rounded-full bg-slate-400" />
                                <span>{item}</span>
                              </li>
                            ))}
                          </ul>
                        </div>
                      )}

                      {data.medications && data.medications.length > 0 && (
                        <div>
                          <p className="text-xs font-semibold uppercase tracking-[0.2em] text-slate-500">Medication</p>
                          <div className="mt-2 space-y-3">
                            {data.medications.map((medication, index) => (
                              <div key={index} className="rounded-2xl border border-slate-200 bg-white p-4">
                                <p className="font-semibold text-slate-900">{medication.name}</p>
                                <p className="mt-1 text-sm text-slate-500">{medication.dosage} • {medication.frequency} • {medication.duration}</p>
                              </div>
                            ))}
                          </div>
                        </div>
                      )}

                      {followUp && (
                        <div>
                          <p className="text-xs font-semibold uppercase tracking-[0.2em] text-slate-500">Follow-up</p>
                          <p className="mt-2 rounded-2xl border border-slate-200 bg-slate-50 p-4 text-sm text-slate-700">{followUp}</p>
                        </div>
                      )}

                      {data.flaggedIssues && data.flaggedIssues.length > 0 && (
                        <div>
                          <p className="text-xs font-semibold uppercase tracking-[0.2em] text-rose-500">Flags</p>
                          <ul className="mt-2 space-y-2 rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-700">
                            {data.flaggedIssues.map((issue, index) => (
                              <li key={index}>{issue}</li>
                            ))}
                          </ul>
                        </div>
                      )}
                    </div>
                  </div>
                </article>
              );
            })}
          </div>
        )}
      </section>
    </div>
  );
}

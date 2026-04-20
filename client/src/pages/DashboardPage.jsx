import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import api from '../services/api';
import { Search, Users, ShieldAlert, MessageSquare, Plus, ArrowRight, Clock3 } from 'lucide-react';

const emptyPatient = {
  name: '',
  age: '',
  gender: 'male',
  phone: '',
  bloodGroup: '',
  allergies: '',
};

export default function DashboardPage() {
  const [patients, setPatients] = useState([]);
  const [query, setQuery] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [formOpen, setFormOpen] = useState(false);
  const [form, setForm] = useState(emptyPatient);
  const [creating, setCreating] = useState(false);

  const [selectedPatientId, setSelectedPatientId] = useState('');
  const [chatQuestion, setChatQuestion] = useState('What were the latest vitals?');
  const [chatAnswer, setChatAnswer] = useState('');
  const [chatSources, setChatSources] = useState([]);
  const [chatBusy, setChatBusy] = useState(false);

  const fetchPatients = async (search = '') => {
    setLoading(true);
    setError('');
    try {
      const { data } = await api.get('/patients', { params: { q: search, limit: 50 } });
      setPatients(data.patients || []);
    } catch (fetchError) {
      setError(fetchError?.response?.data?.error || fetchError.message || 'Failed to load patients');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchPatients();
  }, []);

  const stats = useMemo(() => {
    const total = patients.length;
    const withAllergy = patients.filter((patient) => (patient.allergies || []).length > 0).length;
    const genders = patients.reduce(
      (acc, patient) => {
        const key = patient.gender || 'other';
        acc[key] = (acc[key] || 0) + 1;
        return acc;
      },
      { male: 0, female: 0, other: 0 }
    );

    return { total, withAllergy, genders };
  }, [patients]);

  const onCreatePatient = async (event) => {
    event.preventDefault();
    setCreating(true);
    setError('');

    try {
      await api.post('/patients', {
        name: form.name,
        age: form.age ? Number(form.age) : undefined,
        gender: form.gender,
        phone: form.phone,
        bloodGroup: form.bloodGroup,
        allergies: form.allergies ? form.allergies.split(',').map((item) => item.trim()).filter(Boolean) : [],
      });
      setForm(emptyPatient);
      setFormOpen(false);
      await fetchPatients(query);
    } catch (createError) {
      setError(createError?.response?.data?.error || createError.message || 'Failed to create patient');
    } finally {
      setCreating(false);
    }
  };

  const askQuestion = async () => {
    if (!selectedPatientId || !chatQuestion.trim()) return;

    setChatBusy(true);
    setError('');

    try {
      const { data } = await api.post('/chat', {
        patientId: selectedPatientId,
        query: chatQuestion,
      });
      setChatAnswer(data.answer || 'No answer');
      setChatSources(data.sources || []);
    } catch (chatError) {
      setError(chatError?.response?.data?.error || chatError.message || 'Chat failed');
    } finally {
      setChatBusy(false);
    }
  };

  return (
    <div className="space-y-6">
      <section className="surface rounded-[2rem] p-6 md:p-8">
        <div className="flex flex-col gap-6 lg:flex-row lg:items-end lg:justify-between">
          <div className="max-w-2xl">
            <p className="text-xs font-semibold uppercase tracking-[0.3em] text-slate-500">Clinic dashboard</p>
            <h1 className="mt-3 text-3xl font-bold tracking-tight text-slate-900 md:text-4xl">Simple patient operations in one place</h1>
            <p className="mt-3 text-sm leading-relaxed text-slate-600">
              Search patients, open histories, start consultations, and ask record-based questions without visual clutter.
            </p>
          </div>

          <button
            className="clean-button w-full bg-slate-900 px-5 py-3 text-white lg:w-auto"
            onClick={() => setFormOpen((prev) => !prev)}
            type="button"
          >
            <Plus size={16} /> {formOpen ? 'Close patient form' : 'Add patient'}
          </button>
        </div>

        <div className="mt-6 grid gap-4 md:grid-cols-4">
          <div className="rounded-2xl border border-slate-200 bg-white p-5">
            <div className="flex items-center justify-between text-sm text-slate-500">
              <span>Patients</span><Users size={16} />
            </div>
            <p className="mt-3 text-3xl font-bold text-slate-900">{stats.total}</p>
          </div>
          <div className="rounded-2xl border border-slate-200 bg-white p-5">
            <div className="flex items-center justify-between text-sm text-slate-500">
              <span>Allergy cases</span><ShieldAlert size={16} />
            </div>
            <p className="mt-3 text-3xl font-bold text-slate-900">{stats.withAllergy}</p>
          </div>
          <div className="rounded-2xl border border-slate-200 bg-white p-5">
            <div className="flex items-center justify-between text-sm text-slate-500">
              <span>Male / Female</span><Users size={16} /></div>
            <p className="mt-3 text-3xl font-bold text-slate-900">{stats.genders.male}/{stats.genders.female}</p>
          </div>
          <div className="rounded-2xl border border-slate-200 bg-white p-5">
            <div className="flex items-center justify-between text-sm text-slate-500">
              <span>Quick access</span><Clock3 size={16} />
            </div>
            <p className="mt-3 text-sm text-slate-600">Open history, consultation, or chat from each patient card.</p>
          </div>
        </div>
      </section>

      {error && <p className="rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">{error}</p>}

      {formOpen && (
        <form onSubmit={onCreatePatient} className="surface rounded-[2rem] p-6">
          <div className="mb-4">
            <p className="text-sm font-semibold text-slate-900">Create patient</p>
            <p className="mt-1 text-sm text-slate-500">Keep the record minimal. More details can be added later.</p>
          </div>
          <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
            <input className="clean-input" placeholder="Name" required value={form.name} onChange={(e) => setForm((prev) => ({ ...prev, name: e.target.value }))} />
            <input className="clean-input" placeholder="Age" type="number" value={form.age} onChange={(e) => setForm((prev) => ({ ...prev, age: e.target.value }))} />
            <select className="clean-input" value={form.gender} onChange={(e) => setForm((prev) => ({ ...prev, gender: e.target.value }))}>
              <option value="male">Male</option>
              <option value="female">Female</option>
              <option value="other">Other</option>
            </select>
            <input className="clean-input" placeholder="Phone" value={form.phone} onChange={(e) => setForm((prev) => ({ ...prev, phone: e.target.value }))} />
            <input className="clean-input" placeholder="Blood group" value={form.bloodGroup} onChange={(e) => setForm((prev) => ({ ...prev, bloodGroup: e.target.value }))} />
            <input className="clean-input" placeholder="Allergies (comma separated)" value={form.allergies} onChange={(e) => setForm((prev) => ({ ...prev, allergies: e.target.value }))} />
          </div>
          <div className="mt-4 flex justify-end">
            <button type="submit" disabled={creating} className="clean-button bg-slate-900 px-5 py-3 text-white disabled:opacity-60">
              {creating ? 'Creating...' : 'Save patient'} <ArrowRight size={16} />
            </button>
          </div>
        </form>
      )}

      <section className="grid gap-6 xl:grid-cols-[1.4fr_0.9fr]">
        <div className="surface rounded-[2rem] p-5 md:p-6">
          <div className="mb-4 flex items-center gap-3 rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3">
            <Search size={18} className="text-slate-500" />
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search by name, phone, patient ID"
              className="w-full bg-transparent outline-none"
            />
            <button type="button" className="clean-button bg-slate-900 px-4 py-2 text-white" onClick={() => fetchPatients(query)}>
              Search
            </button>
          </div>

          {loading ? (
            <div className="space-y-3">
              <div className="h-20 animate-pulse rounded-2xl bg-slate-100" />
              <div className="h-20 animate-pulse rounded-2xl bg-slate-100" />
              <div className="h-20 animate-pulse rounded-2xl bg-slate-100" />
            </div>
          ) : (
            <div className="space-y-3">
              {patients.map((patient) => (
                <div key={patient.patientId} className="rounded-2xl border border-slate-200 bg-white p-4 transition hover:border-slate-300 hover:shadow-sm">
                  <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
                    <div>
                      <p className="text-lg font-semibold text-slate-900">{patient.name}</p>
                      <p className="mt-1 text-sm text-slate-500">
                        {patient.patientId} • {patient.gender || 'n/a'} • {patient.age || '-'} yrs
                      </p>
                    </div>
                    <div className="flex flex-wrap gap-2">
                      <Link className="clean-button border border-slate-200 bg-white px-4 py-2 text-slate-700 hover:bg-slate-50" to={`/patient-history/${patient.patientId}`}>
                        View history
                      </Link>
                      <Link className="clean-button bg-slate-900 px-4 py-2 text-white" to={`/consultation?patientId=${patient.patientId}`}>
                        Start consultation
                      </Link>
                      <button type="button" className="clean-button border border-slate-200 bg-white px-4 py-2 text-slate-700 hover:bg-slate-50" onClick={() => setSelectedPatientId(patient.patientId)}>
                        Use in chat
                      </button>
                    </div>
                  </div>
                </div>
              ))}
              {!patients.length && <div className="rounded-2xl border border-dashed border-slate-200 bg-slate-50 p-8 text-center text-slate-500">No patients found.</div>}
            </div>
          )}
        </div>

        <aside className="surface rounded-[2rem] p-5 md:p-6">
          <div className="mb-4">
            <p className="text-sm font-semibold text-slate-900">Patient Q&A</p>
            <p className="mt-1 text-sm text-slate-500">Ask record-based questions using the selected patient ID.</p>
          </div>

          <div className="space-y-3">
            <input
              className="clean-input"
              placeholder="Patient ID"
              value={selectedPatientId}
              onChange={(e) => setSelectedPatientId(e.target.value)}
            />
            <textarea
              className="clean-input min-h-28 resize-none"
              value={chatQuestion}
              onChange={(e) => setChatQuestion(e.target.value)}
            />
            <button type="button" className="clean-button w-full bg-slate-900 px-4 py-3 text-white disabled:opacity-60" onClick={askQuestion} disabled={chatBusy}>
              <MessageSquare size={16} /> {chatBusy ? 'Asking...' : 'Ask question'}
            </button>
          </div>

          <div className="mt-5 rounded-2xl border border-slate-200 bg-slate-50 p-4">
            <p className="text-sm leading-relaxed text-slate-700">{chatAnswer || 'Answers will appear here.'}</p>
            {!!chatSources.length && (
              <div className="mt-3 space-y-1 text-xs text-slate-500">
                {chatSources.map((src) => (
                  <p key={src.sessionId}>{src.sessionId} • Visit {src.visitNumber}</p>
                ))}
              </div>
            )}
          </div>
        </aside>
      </section>
    </div>
  );
}

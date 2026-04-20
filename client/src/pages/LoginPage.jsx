import { useState } from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { BriefcaseMedical, ShieldCheck, Sparkles, ArrowRight, Stethoscope } from 'lucide-react';

const defaultRegister = {
  name: '',
  email: '',
  password: '',
  specialization: '',
  licenseNumber: '',
};

export default function LoginPage() {
  const { user, login, register } = useAuth();
  const [mode, setMode] = useState('login');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [loginForm, setLoginForm] = useState({ email: '', password: '' });
  const [registerForm, setRegisterForm] = useState(defaultRegister);

  if (user) return <Navigate to="/" replace />;

  const onSubmit = async (event) => {
    event.preventDefault();
    setBusy(true);
    setError('');

    try {
      if (mode === 'login') {
        await login(loginForm);
      } else {
        await register(registerForm);
      }
    } catch (submitError) {
      setError(submitError?.response?.data?.error || submitError.message || 'Request failed');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="min-h-screen bg-slate-50 text-slate-900">
      <div className="mx-auto grid min-h-screen max-w-6xl grid-cols-1 gap-8 px-4 py-8 md:grid-cols-[1.05fr_0.95fr] md:items-center">
        <section className="surface relative overflow-hidden rounded-[2rem] bg-white p-8 md:p-10">
          <div className="absolute right-0 top-0 h-56 w-56 translate-x-1/3 -translate-y-1/3 rounded-full bg-sky-100 blur-3xl" />
          <div className="relative z-10 max-w-xl">
            <div className="mb-6 inline-flex items-center gap-2 rounded-full border border-slate-200 bg-white px-3 py-1 text-xs font-medium text-slate-600 shadow-sm">
              <ShieldCheck size={14} className="text-sky-600" /> Clean clinic workspace
            </div>
            <div className="flex items-center gap-3 text-slate-900">
              <span className="flex h-12 w-12 items-center justify-center rounded-2xl bg-slate-900 text-white">
                <BriefcaseMedical size={20} />
              </span>
              <h1 className="text-4xl font-bold tracking-tight">VoiceCare</h1>
            </div>
            <p className="mt-4 text-lg leading-relaxed text-slate-600">A simple clinic console for patient records, consultations, and history lookup.</p>

            <div className="mt-8 grid gap-3 text-sm text-slate-600">
              <div className="flex items-center gap-3 rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3">
                <Sparkles size={16} className="text-sky-600" /> Fast consultations and clean records
              </div>
              <div className="flex items-center gap-3 rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3">
                <Stethoscope size={16} className="text-slate-700" /> Patient timeline and prescriptions in one place
              </div>
              <div className="flex items-center gap-3 rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3">
                <ShieldCheck size={16} className="text-emerald-600" /> Secure doctor login and registration
              </div>
            </div>
          </div>
        </section>

        <section className="surface rounded-[2rem] bg-white p-6 md:p-8">
          <div className="mb-6 flex rounded-full border border-slate-200 bg-slate-50 p-1">
            <button
              type="button"
              className={`w-1/2 rounded-full px-4 py-2 text-sm font-semibold transition ${mode === 'login' ? 'bg-slate-900 text-white shadow-sm' : 'text-slate-500'}`}
              onClick={() => setMode('login')}
            >
              Login
            </button>
            <button
              type="button"
              className={`w-1/2 rounded-full px-4 py-2 text-sm font-semibold transition ${mode === 'register' ? 'bg-slate-900 text-white shadow-sm' : 'text-slate-500'}`}
              onClick={() => setMode('register')}
            >
              Register
            </button>
          </div>

          <form onSubmit={onSubmit} className="space-y-4">
            {mode === 'login' ? (
              <>
                <input
                  className="clean-input"
                  placeholder="Email"
                  type="email"
                  required
                  value={loginForm.email}
                  onChange={(e) => setLoginForm((prev) => ({ ...prev, email: e.target.value }))}
                />
                <input
                  className="clean-input"
                  placeholder="Password"
                  type="password"
                  required
                  value={loginForm.password}
                  onChange={(e) => setLoginForm((prev) => ({ ...prev, password: e.target.value }))}
                />
              </>
            ) : (
              <>
                <input
                  className="clean-input"
                  placeholder="Doctor Name"
                  required
                  value={registerForm.name}
                  onChange={(e) => setRegisterForm((prev) => ({ ...prev, name: e.target.value }))}
                />
                <input
                  className="clean-input"
                  placeholder="Email"
                  type="email"
                  required
                  value={registerForm.email}
                  onChange={(e) => setRegisterForm((prev) => ({ ...prev, email: e.target.value }))}
                />
                <input
                  className="clean-input"
                  placeholder="Password"
                  type="password"
                  required
                  value={registerForm.password}
                  onChange={(e) => setRegisterForm((prev) => ({ ...prev, password: e.target.value }))}
                />
                <input
                  className="clean-input"
                  placeholder="Specialization"
                  value={registerForm.specialization}
                  onChange={(e) => setRegisterForm((prev) => ({ ...prev, specialization: e.target.value }))}
                />
                <input
                  className="clean-input"
                  placeholder="License Number"
                  value={registerForm.licenseNumber}
                  onChange={(e) => setRegisterForm((prev) => ({ ...prev, licenseNumber: e.target.value }))}
                />
              </>
            )}

            {error && <p className="rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">{error}</p>}

            <button
              type="submit"
              disabled={busy}
              className="clean-button w-full bg-slate-900 px-4 py-3 font-semibold text-white hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-70"
            >
              {busy ? 'Please wait...' : mode === 'login' ? 'Sign In' : 'Create Account'} <ArrowRight size={16} />
            </button>
          </form>
        </section>
      </div>
    </div>
  );
}

import { Link, NavLink, Outlet } from 'react-router-dom';
import { BriefcaseMedical, LayoutDashboard, FileText, LogOut } from 'lucide-react';
import { useAuth } from '../../context/AuthContext';

const navClass = ({ isActive }) =>
  [
    'inline-flex items-center gap-2 rounded-full px-4 py-2 text-sm font-medium transition',
    isActive
      ? 'bg-slate-900 text-white shadow-sm'
      : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900',
  ].join(' ');

export default function Layout() {
  const { user, logout } = useAuth();

  return (
    <div className="min-h-screen text-slate-900">
      <header className="sticky top-0 z-20 border-b border-slate-200/80 bg-white/80 backdrop-blur-xl">
        <div className="page-shell flex w-full items-center justify-between py-4">
          <Link to="/" className="flex items-center gap-3 text-lg font-semibold tracking-tight text-slate-900">
            <span className="flex h-10 w-10 items-center justify-center rounded-2xl bg-slate-900 text-white shadow-sm">
              <BriefcaseMedical size={18} />
            </span>
            <span>VoiceCare</span>
          </Link>

          <nav className="hidden items-center gap-2 rounded-full border border-slate-200 bg-white p-1 md:flex">
            <NavLink to="/" className={navClass} end>
              <LayoutDashboard size={16} /> Dashboard
            </NavLink>
            <NavLink to="/consultation" className={navClass}>
              <FileText size={16} /> Consultation
            </NavLink>
          </nav>

          <div className="flex items-center gap-3">
            <span className="hidden text-sm text-slate-500 md:inline">{user?.name || user?.email}</span>
            <button
              className="clean-button border-slate-200 bg-white text-slate-700 hover:border-slate-300 hover:bg-slate-50"
              onClick={logout}
            >
              <LogOut size={16} /> Logout
            </button>
          </div>
        </div>
        <div className="page-shell pb-4 md:hidden">
          <nav className="flex items-center gap-2 overflow-x-auto rounded-2xl border border-slate-200 bg-white p-1">
            <NavLink to="/" className={navClass} end>
              <LayoutDashboard size={16} /> Dashboard
            </NavLink>
            <NavLink to="/consultation" className={navClass}>
              <FileText size={16} /> Consultation
            </NavLink>
          </nav>
        </div>
      </header>

      <main className="page-shell py-8">
        <Outlet />
      </main>
    </div>
  );
}

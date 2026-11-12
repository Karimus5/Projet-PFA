import { useState } from 'react';
import { Link, NavLink, useLocation } from 'react-router';
import { useAuth } from '../auth/AuthContext';
import { ROLE_LABELS } from '../config';
import { navItemsFor } from '../navigation';

function NavItem({ item, onNavigate, vertical }) {
  const { pathname } = useLocation();
  const base = vertical ? 'block rounded-md px-3 py-2 text-base' : 'rounded-md px-3 py-2 text-sm';
  if (!item.available) {
    return (
      <span
        aria-disabled="true"
        title="Fonctionnalité à venir"
        className={`${base} flex cursor-not-allowed items-center gap-2 font-medium text-slate-400`}
      >
        {item.label}
        <span className="rounded bg-slate-100 px-1.5 py-0.5 text-xs font-normal text-slate-500">Bientôt</span>
      </span>
    );
  }
  return (
    <NavLink
      to={item.to}
      onClick={onNavigate}
      className={({ isActive }) => {
        const active = isActive && !(item.exclude && pathname.startsWith(item.exclude));
        return `${base} font-medium ${active ? 'bg-indigo-50 text-indigo-700' : 'text-slate-700 hover:bg-slate-100'}`;
      }}
    >
      {item.label}
    </NavLink>
  );
}

export default function Navbar() {
  const { user, logout } = useAuth();
  const [open, setOpen] = useState(false);
  const items = user ? navItemsFor(user.role) : [];

  return (
    <header className="border-b border-slate-200 bg-white">
      <nav className="mx-auto flex max-w-6xl items-center justify-between gap-4 px-4 py-3" aria-label="Navigation principale">
        <Link to="/" className="flex items-center gap-2 text-lg font-semibold text-indigo-700">
          <span aria-hidden="true" className="flex h-8 w-8 items-center justify-center rounded-md bg-indigo-600 text-white">R</span>
          Réclamations
        </Link>

        {user ? (
          <>
            {/* Bureau */}
            <div className="hidden flex-1 items-center gap-1 md:flex">
              {items.map((item) => (
                <NavItem key={item.label} item={item} />
              ))}
            </div>
            <div className="hidden items-center gap-3 md:flex">
              <span className="text-right text-sm leading-tight">
                <span className="block font-medium text-slate-900">{user.name}</span>
                <span className="block text-xs text-slate-500">{ROLE_LABELS[user.role] ?? user.role}</span>
              </span>
              <button type="button" className="btn-secondary" onClick={() => logout()}>
                Déconnexion
              </button>
            </div>

            {/* Mobile */}
            <button
              type="button"
              className="btn-secondary md:hidden"
              aria-expanded={open}
              aria-controls="menu-mobile"
              onClick={() => setOpen((value) => !value)}
            >
              {open ? 'Fermer' : 'Menu'}
            </button>
          </>
        ) : (
          <div className="flex items-center gap-2">
            <NavLink to="/connexion" className="btn-secondary">Connexion</NavLink>
            <NavLink to="/inscription" className="btn-primary">Inscription</NavLink>
          </div>
        )}
      </nav>

      {user && open && (
        <div id="menu-mobile" className="border-t border-slate-200 px-4 py-3 md:hidden">
          <p className="mb-2 text-sm text-slate-600">
            {user.name} · {ROLE_LABELS[user.role] ?? user.role}
          </p>
          <div className="space-y-1">
            {items.map((item) => (
              <NavItem key={item.label} item={item} vertical onNavigate={() => setOpen(false)} />
            ))}
          </div>
          <button type="button" className="btn-secondary mt-3 w-full" onClick={() => logout()}>
            Déconnexion
          </button>
        </div>
      )}
    </header>
  );
}

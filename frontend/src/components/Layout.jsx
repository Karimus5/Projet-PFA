import { Outlet } from 'react-router';
import Navbar from './Navbar';

export default function Layout() {
  return (
    <div className="flex min-h-screen flex-col bg-slate-50 dark:bg-slate-950 text-slate-900 dark:text-slate-100">
      <Navbar />
      {/* Colonne flex : une page peut remplir la hauteur restante (flex-1, voir CenteredPage).
          *:w-full garde aux autres pages la largeur qu'elles auraient en flux normal. */}
      <main className="mx-auto flex w-full max-w-6xl flex-1 flex-col px-4 py-8 *:w-full">
        <Outlet />
      </main>
      <footer className="border-t border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900 py-4 text-center text-xs text-slate-500 dark:text-slate-400">
        Plateforme de gestion des réclamations — PFA EMSI 2026-2027
      </footer>
    </div>
  );
}

import { useEffect, useState } from 'react';
import { applyTheme, onSystemThemeChange, resolveTheme, setTheme } from '../theme/theme';

/** Bouton de bascule clair / sombre (dans la barre de navigation). */
export default function ThemeToggle({ className = '' }) {
  const [theme, setThemeState] = useState(resolveTheme);

  useEffect(() => {
    applyTheme(theme);
  }, [theme]);

  useEffect(
    () => onSystemThemeChange((next) => setThemeState(next)),
    [],
  );

  const dark = theme === 'dark';
  const label = dark ? 'Activer le thème clair' : 'Activer le thème sombre';

  return (
    <button
      type="button"
      className={`btn-secondary px-2.5 ${className}`}
      aria-label={label}
      title={label}
      onClick={() => {
        const next = dark ? 'light' : 'dark';
        setTheme(next);
        setThemeState(next);
      }}
    >
      {dark ? (
        // Soleil
        <svg aria-hidden="true" viewBox="0 0 24 24" className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
          <circle cx="12" cy="12" r="4" />
          <path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M4.93 19.07l1.41-1.41M17.66 6.34l1.41-1.41" />
        </svg>
      ) : (
        // Lune
        <svg aria-hidden="true" viewBox="0 0 24 24" className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z" />
        </svg>
      )}
    </button>
  );
}

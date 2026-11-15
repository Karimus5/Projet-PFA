/**
 * Thème clair / sombre (hors cahier des charges, confort d'affichage).
 *
 * - Le thème sombre est activé par la classe `dark` sur <html> (variante Tailwind `dark:`,
 *   déclarée dans index.css).
 * - Choix explicite de l'utilisateur mémorisé dans localStorage (donnée non sensible,
 *   contrairement au jeton). Sans choix : on suit le réglage du système.
 * - index.html contient un petit script qui applique la même règle AVANT le rendu React,
 *   pour éviter un flash blanc au chargement : garder la même clé que STORAGE_KEY.
 */
export const STORAGE_KEY = 'theme';

const systemQuery = () => window.matchMedia?.('(prefers-color-scheme: dark)');

/** 'light' | 'dark' | null (aucun choix : suivre le système). */
export function getStoredTheme() {
  try {
    const value = window.localStorage.getItem(STORAGE_KEY);
    return value === 'light' || value === 'dark' ? value : null;
  } catch {
    return null; // stockage indisponible (navigation privée stricte, etc.)
  }
}

export function resolveTheme() {
  return getStoredTheme() ?? (systemQuery()?.matches ? 'dark' : 'light');
}

export function applyTheme(theme) {
  document.documentElement.classList.toggle('dark', theme === 'dark');
}

export function setTheme(theme) {
  try {
    window.localStorage.setItem(STORAGE_KEY, theme);
  } catch {
    // le thème reste appliqué pour la session même s'il n'est pas mémorisé
  }
  applyTheme(theme);
}

/** Appelle `callback` quand le thème du système change (seulement si aucun choix explicite). */
export function onSystemThemeChange(callback) {
  const query = systemQuery();
  if (!query) return () => {};
  const listener = () => {
    if (!getStoredTheme()) callback(resolveTheme());
  };
  query.addEventListener('change', listener);
  return () => query.removeEventListener('change', listener);
}

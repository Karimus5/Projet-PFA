// Mêmes règles que le backend (auth-service, Dtos.java) pour un retour immédiat à l'utilisateur.
// Le backend revalide tout : ces contrôles ne servent qu'au confort.

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function validateName(name) {
  const value = (name ?? '').trim();
  if (!value) return 'Le nom est obligatoire';
  if (value.length > 100) return 'Le nom ne doit pas dépasser 100 caractères';
  return null;
}

export function validateEmail(email) {
  const value = (email ?? '').trim();
  if (!value) return "L'email est obligatoire";
  if (!EMAIL_RE.test(value) || value.length > 255) return "L'email n'est pas valide";
  return null;
}

export function validatePassword(password) {
  if (!password) return 'Le mot de passe est obligatoire';
  if (password.length < 8 || password.length > 72) {
    return 'Le mot de passe doit contenir entre 8 et 72 caractères';
  }
  return null;
}

/** Retire les entrées nulles : { champ: message } ne contient que les erreurs réelles. */
export function compact(errors) {
  return Object.fromEntries(Object.entries(errors).filter(([, message]) => Boolean(message)));
}

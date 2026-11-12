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

// --- Réclamations (claim-service, Dtos.java) ---

/** Champ texte obligatoire de longueur maximale donnée (message identique au backend). */
export function validateRequiredText(value, max, requiredMessage, tooLongMessage) {
  const text = (value ?? '').trim();
  if (!text) return requiredMessage;
  if (text.length > max) return tooLongMessage;
  return null;
}

export const validateClaimTitle = (value) =>
  validateRequiredText(value, 150, 'Le titre est obligatoire', 'Le titre ne doit pas dépasser 150 caractères');

export const validateClaimDescription = (value) =>
  validateRequiredText(value, 5000, 'La description est obligatoire',
    'La description ne doit pas dépasser 5000 caractères');

export const validateComment = (value) =>
  validateRequiredText(value, 2000, 'Le commentaire est obligatoire',
    'Le commentaire ne doit pas dépasser 2000 caractères');

export const validateComplement = (value) =>
  validateRequiredText(value, 2000, 'Le complément est obligatoire',
    'Le complément ne doit pas dépasser 2000 caractères');

export function validateNote(value) {
  return (value ?? '').trim().length > 500 ? 'La note ne doit pas dépasser 500 caractères' : null;
}

export const validateRefName = (value) =>
  validateRequiredText(value, 100, 'Le nom est obligatoire', 'Le nom ne doit pas dépasser 100 caractères');

export function validateRefDescription(value) {
  return (value ?? '').trim().length > 255 ? 'La description ne doit pas dépasser 255 caractères' : null;
}

/** Retire les entrées nulles : { champ: message } ne contient que les erreurs réelles. */
export function compact(errors) {
  return Object.fromEntries(Object.entries(errors).filter(([, message]) => Boolean(message)));
}

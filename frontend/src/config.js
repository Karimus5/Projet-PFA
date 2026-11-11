// URL de l'API Gateway (seul point d'entrée du backend). Configurable avec VITE_API_URL.
export const API_URL = (import.meta.env.VITE_API_URL || 'http://localhost:8080').replace(/\/+$/, '');

export const ROLE_LABELS = {
  CLIENT: 'Client',
  AGENT: 'Agent',
  ADMIN: 'Administrateur',
};

export const ROLES = Object.keys(ROLE_LABELS);

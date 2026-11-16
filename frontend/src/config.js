// URL de l'API Gateway (seul point d'entrée du backend). Configurable avec VITE_API_URL.
export const API_URL = (import.meta.env.VITE_API_URL || 'http://localhost:8080').replace(/\/+$/, '');

export const ROLE_LABELS = {
  CLIENT: 'Client',
  AGENT: 'Agent',
  ADMIN: 'Administrateur',
};

export const ROLES = Object.keys(ROLE_LABELS);

/**
 * Auteurs possibles dans l'historique et les commentaires : les rôles, plus SYSTEM (rôle interne du
 * claim-service, auteur des classements automatiques). SYSTEM n'est PAS un rôle d'utilisateur :
 * il n'apparaît jamais dans ROLES (choix de rôle de l'administration).
 */
export const AUTHOR_LABELS = { ...ROLE_LABELS, SYSTEM: 'Système' };

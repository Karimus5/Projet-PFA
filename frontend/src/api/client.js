import { API_URL } from '../config';
import { tokenStorage } from '../auth/tokenStorage';

/**
 * Erreur renvoyée par l'API, au format ApiError du backend :
 * { status, message, fieldErrors? } (fieldErrors : champ → message, pour les 400 de validation).
 */
export class ApiError extends Error {
  constructor(status, message, fieldErrors = {}) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}

const DEFAULT_MESSAGES = {
  0: "Impossible de joindre le serveur. Vérifiez que l'API Gateway est démarrée.",
  400: 'Données invalides.',
  401: 'Authentification requise.',
  403: 'Accès refusé.',
  404: 'Ressource introuvable.',
  409: 'Conflit avec des données existantes.',
  500: 'Erreur interne du serveur.',
  503: 'Service temporairement indisponible. Réessayez dans quelques instants.',
};

let unauthorizedHandler = null;

/** Appelé (par AuthContext) quand l'API répond 401 à une requête qui portait un jeton. */
export function setUnauthorizedHandler(handler) {
  unauthorizedHandler = handler;
}

async function readJson(response) {
  const text = await response.text();
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

/**
 * Appel à l'API Gateway. Ajoute automatiquement « Authorization: Bearer <jeton> » (sauf auth: false).
 * Renvoie le JSON de la réponse, ou lève une ApiError avec un message en français.
 *
 * - body : objet (envoyé en JSON) ou FormData (multipart : le navigateur pose lui-même le
 *   Content-Type avec sa « boundary », il ne faut donc pas le fixer ici) ;
 * - responseType: 'blob' : renvoie le contenu binaire (téléchargement de pièce jointe). Le jeton
 *   reste dans l'en-tête Authorization : il n'apparaît jamais dans une URL.
 */
export async function apiRequest(path, { method = 'GET', body, auth = true, responseType = 'json' } = {}) {
  const isForm = typeof FormData !== 'undefined' && body instanceof FormData;
  const headers = { Accept: responseType === 'blob' ? '*/*' : 'application/json' };
  if (body !== undefined && !isForm) headers['Content-Type'] = 'application/json';

  const token = auth ? tokenStorage.get() : null;
  if (token) headers.Authorization = `Bearer ${token}`;

  let response;
  try {
    response = await fetch(`${API_URL}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : isForm ? body : JSON.stringify(body),
    });
  } catch {
    throw new ApiError(0, DEFAULT_MESSAGES[0]);
  }

  if (response.ok && responseType === 'blob') return response.blob();
  const data = await readJson(response);
  if (response.ok) return data;

  // Jeton expiré, falsifié ou refusé : déconnexion centralisée.
  // (Sans jeton, ex. mauvais mot de passe au login, le 401 est une simple erreur affichée.)
  if (response.status === 401 && token && unauthorizedHandler) {
    unauthorizedHandler();
  }

  const status = response.status;
  const message = data?.message || DEFAULT_MESSAGES[status] || `Erreur inattendue (${status}).`;
  throw new ApiError(status, message, data?.fieldErrors ?? {});
}

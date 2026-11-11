/**
 * SEUL endroit du code qui lit ou écrit le jeton d'accès.
 *
 * Choix : sessionStorage.
 *  + le jeton survit au rechargement de la page ;
 *  + il est effacé à la fermeture de l'onglet (fenêtre d'exposition plus courte que localStorage) ;
 *  - il reste lisible par du JavaScript : une faille XSS permettrait de le voler.
 * Alternative plus sûre (non retenue à ce stade, car elle demande de modifier le backend) :
 * cookie HttpOnly posé par le serveur + protection CSRF.
 * Pour changer de stratégie, il suffit de modifier ce fichier.
 */
const KEY = 'reclamations.accessToken';

function storage() {
  try {
    return window.sessionStorage;
  } catch {
    return null; // stockage bloqué par le navigateur
  }
}

export const tokenStorage = {
  get() {
    return storage()?.getItem(KEY) ?? null;
  },
  set(token) {
    storage()?.setItem(KEY, token);
  },
  clear() {
    storage()?.removeItem(KEY);
  },
};

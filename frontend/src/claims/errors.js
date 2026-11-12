/**
 * Message d'erreur affiché pour une action sur une réclamation. Le texte du serveur (ApiError.message)
 * est déjà en français ; on précise seulement la nature de l'erreur.
 */
export function describeError(err) {
  const message = err?.message || 'Erreur inattendue.';
  switch (err?.status) {
    case 400:
      return err.fieldErrors && Object.keys(err.fieldErrors).length > 0
        ? 'Certains champs sont invalides : corrigez-les puis réessayez.'
        : `Requête invalide : ${message}`;
    case 403:
      return `Action non autorisée : ${message}`;
    case 404:
      return `Introuvable : ${message}`;
    case 409:
      return `Action impossible : ${message}`;
    default:
      return message;
  }
}

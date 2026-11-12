/**
 * Contrôles côté client des pièces jointes, identiques aux limites du claim-service
 * (AllowedFileType, claims.attachments.* dans config-repo/claim-service.yml).
 * Le serveur vérifie en plus la signature binaire du fichier : un faux PDF sera refusé (400).
 */
export const MAX_FILE_SIZE = 5 * 1024 * 1024; // 5 Mo
export const MAX_FILES_PER_CLAIM = 5;
export const ACCEPT = '.pdf,.png,.jpg,.jpeg,application/pdf,image/png,image/jpeg';

const ALLOWED_EXTENSIONS = ['pdf', 'png', 'jpg', 'jpeg'];
const ALLOWED_TYPES = ['application/pdf', 'image/png', 'image/jpeg', ''];

function extensionOf(name) {
  const dot = name.lastIndexOf('.');
  return dot >= 0 ? name.slice(dot + 1).toLowerCase() : '';
}

/**
 * @param files fichiers choisis
 * @param alreadyAttached nombre de pièces jointes déjà présentes sur la réclamation
 * @returns liste de messages d'erreur (vide si tout est valide)
 */
export function validateFiles(files, alreadyAttached = 0) {
  const errors = [];
  if (alreadyAttached + files.length > MAX_FILES_PER_CLAIM) {
    const remaining = Math.max(0, MAX_FILES_PER_CLAIM - alreadyAttached);
    errors.push(
      `${MAX_FILES_PER_CLAIM} pièces jointes maximum par réclamation : vous pouvez encore en ajouter ${remaining}.`,
    );
  }
  for (const file of files) {
    if (!ALLOWED_EXTENSIONS.includes(extensionOf(file.name)) || !ALLOWED_TYPES.includes(file.type ?? '')) {
      errors.push(`${file.name} : type non accepté (PDF, PNG ou JPG uniquement).`);
    } else if (file.size > MAX_FILE_SIZE) {
      errors.push(`${file.name} : fichier trop volumineux (5 Mo maximum).`);
    } else if (file.size === 0) {
      errors.push(`${file.name} : fichier vide.`);
    }
  }
  return errors;
}

/** Enregistre un Blob sous le nom donné (lien temporaire en mémoire, jamais d'URL contenant le jeton). */
export function saveBlob(blob, fileName) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 0);
}

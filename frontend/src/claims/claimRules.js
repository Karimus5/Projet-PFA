/**
 * Règles d'affichage des réclamations, recopiées du claim-service (ClaimStatus, ClaimWorkflow,
 * ClaimService, AttachmentService). Elles servent UNIQUEMENT à masquer ou désactiver les actions
 * interdites : le backend revérifie tout (404 / 403 / 409).
 * En cas de changement du workflow côté serveur, mettre ce fichier à jour.
 */

export const STATUSES = ['NEW', 'CLASSIFIED', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED', 'CLOSED', 'REJECTED'];

export const STATUS_LABELS = {
  NEW: 'Nouvelle',
  CLASSIFIED: 'Classée',
  ASSIGNED: 'Affectée',
  IN_PROGRESS: 'En cours',
  RESOLVED: 'Résolue',
  CLOSED: 'Clôturée',
  REJECTED: 'Rejetée',
};

export const STATUS_STYLES = {
  NEW: 'bg-sky-100 dark:bg-sky-900/50 text-sky-800 dark:text-sky-200',
  CLASSIFIED: 'bg-violet-100 dark:bg-violet-900/50 text-violet-800 dark:text-violet-200',
  ASSIGNED: 'bg-indigo-100 dark:bg-indigo-900/50 text-indigo-800 dark:text-indigo-200',
  IN_PROGRESS: 'bg-amber-100 dark:bg-amber-900/50 text-amber-800 dark:text-amber-200',
  RESOLVED: 'bg-emerald-100 dark:bg-emerald-900/50 text-emerald-800 dark:text-emerald-200',
  CLOSED: 'bg-slate-200 dark:bg-slate-700 text-slate-700 dark:text-slate-300',
  REJECTED: 'bg-red-100 dark:bg-red-900/50 text-red-800 dark:text-red-200',
};

/** Réclamation « ouverte » : commentaires, compléments et pièces jointes encore possibles. */
export function isOpen(status) {
  return status !== 'CLOSED' && status !== 'REJECTED';
}

const STAFF = ['AGENT', 'ADMIN'];

/**
 * Transitions réalisables par PATCH /status (ClaimWorkflow), avec le libellé du bouton.
 * NEW → CLASSIFIED et CLASSIFIED → ASSIGNED passent par les formulaires de classement et d'affectation.
 */
const STATUS_ACTIONS = [
  { from: 'NEW', to: 'REJECTED', roles: ['ADMIN'], label: 'Rejeter', style: 'btn-danger', needsNote: true },
  { from: 'ASSIGNED', to: 'IN_PROGRESS', roles: STAFF, label: 'Prendre en charge', style: 'btn-primary' },
  { from: 'IN_PROGRESS', to: 'RESOLVED', roles: STAFF, label: 'Marquer comme résolue', style: 'btn-success' },
  { from: 'RESOLVED', to: 'CLOSED', roles: ['CLIENT'], label: 'Clôturer', style: 'btn-success' },
  { from: 'RESOLVED', to: 'IN_PROGRESS', roles: ['CLIENT'], label: 'Rouvrir', style: 'btn-secondary' },
];

/** Boutons de changement de statut proposés à l'utilisateur pour cette réclamation. */
export function statusActionsFor(claim, user) {
  if (!claim || !user) return [];
  if (user.role === 'CLIENT' && claim.clientId !== user.id) return [];
  return STATUS_ACTIONS.filter((action) => action.from === claim.status && action.roles.includes(user.role));
}

/** Classement initial (NEW → CLASSIFIED) : administrateur seulement. */
export function canClassifyNew(claim, user) {
  return user?.role === 'ADMIN' && claim?.status === 'NEW';
}

/** Correction de la catégorie / du service (statut conservé) : agent ou admin, avant résolution. */
export function canCorrectClassification(claim, user) {
  return STAFF.includes(user?.role) && ['CLASSIFIED', 'ASSIGNED', 'IN_PROGRESS'].includes(claim?.status);
}

/** Affectation (CLASSIFIED, agent facultatif) ou réaffectation (ASSIGNED / IN_PROGRESS, agent obligatoire). */
export function canAssign(claim, user) {
  return STAFF.includes(user?.role) && ['CLASSIFIED', 'ASSIGNED', 'IN_PROGRESS'].includes(claim?.status);
}

export function agentRequiredForAssignment(claim) {
  return claim?.status !== 'CLASSIFIED';
}

export function canSeeInternalComments(user) {
  return STAFF.includes(user?.role);
}

/** Complément d'information (BF9) : le client propriétaire, réclamation ouverte. */
export function canAddComplement(claim, user) {
  return user?.role === 'CLIENT' && claim?.clientId === user.id && isOpen(claim.status);
}

/**
 * Commentaires visibles : un client ne voit JAMAIS de commentaire interne. Le serveur les filtre
 * déjà en base ; ce filtre est une seconde barrière côté interface.
 */
export function visibleComments(comments, user) {
  const list = comments ?? [];
  return canSeeInternalComments(user) ? list : list.filter((comment) => !comment.internal);
}

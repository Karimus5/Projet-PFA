/**
 * Règles d'affichage de la classification automatique (BF14, BF16-BF20), recopiées du
 * claim-service (ClaimService.confirmClassification / applyAiResult, AiPredictionOutcome,
 * AiFailureReason). Elles servent UNIQUEMENT à afficher ou masquer : le serveur revérifie tout
 * (403 / 404 / 409). À mettre à jour si les règles du serveur changent.
 *
 * aiPrediction (ClaimDetail, agents et admins seulement ; null pour un client) :
 * { outcome, failureReason, predictedCategory, predictedService, confidence, modelVersion,
 *   predictedAt, decision, finalCategory, finalService, decidedByRole, decidedAt }
 */

const STAFF = ['AGENT', 'ADMIN'];

/** Statuts où la classification peut encore être confirmée ou corrigée. */
const REVIEWABLE_STATUSES = ['CLASSIFIED', 'ASSIGNED', 'IN_PROGRESS'];

/**
 * Seuil de confiance du modèle v1.0.0 (ai-classifier-service/model/metadata.json). Le serveur ne
 * le renvoie pas dans aiPrediction : il ne sert ici qu'à choisir la couleur du niveau de confiance.
 */
export const MODEL_THRESHOLD = 0.57;
export const HIGH_CONFIDENCE = 0.85;

/** Classement automatique appliqué, pas encore confirmé ni corrigé : boutons Confirmer / Corriger. */
export function canReviewAiClassification(claim, user) {
  const prediction = claim?.aiPrediction;
  return STAFF.includes(user?.role)
    && claim?.classificationSource === 'AI'
    && prediction?.outcome === 'APPLIED'
    && !prediction.decision
    && REVIEWABLE_STATUSES.includes(claim.status);
}

/** Badge « IA » / « Classée automatiquement » : la catégorie actuelle vient du service IA. */
export function isAiClassified(claim) {
  return claim?.classificationSource === 'AI';
}

/**
 * Confiance en pourcentage, TRONQUÉE comme côté serveur (ClaimService.percent) : 0,5695 → « 56 % »,
 * jamais plus que la valeur réelle. La marge 1e-9 corrige les flottants (0,57 × 100 = 56,999…).
 */
export function formatConfidence(value) {
  if (value == null || Number.isNaN(Number(value))) return '—';
  return `${Math.floor(Number(value) * 100 + 1e-9)} %`;
}

/** Niveau de confiance : libellé et couleurs (badge et barre). */
export function confidenceLevel(value) {
  if (value == null) return null;
  if (value >= HIGH_CONFIDENCE) {
    return {
      label: 'Confiance élevée',
      badge: 'bg-emerald-100 dark:bg-emerald-900/50 text-emerald-800 dark:text-emerald-200',
      bar: 'bg-emerald-500 dark:bg-emerald-400',
    };
  }
  if (value >= MODEL_THRESHOLD) {
    return {
      label: 'Confiance moyenne',
      badge: 'bg-amber-100 dark:bg-amber-900/50 text-amber-800 dark:text-amber-200',
      bar: 'bg-amber-500 dark:bg-amber-400',
    };
  }
  return {
    label: 'Confiance faible',
    badge: 'bg-red-100 dark:bg-red-900/50 text-red-800 dark:text-red-200',
    bar: 'bg-red-500 dark:bg-red-400',
  };
}

/** Pourquoi le classement automatique n'a pas été appliqué (sans détail technique inutile). */
export const NOT_APPLIED_TEXTS = {
  LOW_CONFIDENCE: 'Confiance trop faible : la proposition de l’IA n’a pas été retenue.',
  UNKNOWN_CATEGORY: 'La catégorie proposée par l’IA n’existe pas dans le référentiel.',
  UNAVAILABLE: 'Le service IA était indisponible lors de la soumission.',
};

/** Auteur d'une décision : le serveur ne renvoie que son rôle (ni nom ni identifiant). */
export const DECIDED_BY_LABELS = {
  AGENT: 'un agent',
  ADMIN: 'un administrateur',
};

export const DECISION_LABELS = {
  CONFIRMED: 'Confirmée',
  CORRECTED: 'Corrigée',
};

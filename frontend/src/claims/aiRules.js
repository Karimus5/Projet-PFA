/**
 * Règles d'affichage de la classification automatique (BF14, BF16-BF20), recopiées du
 * claim-service (ClaimService.confirmClassification / applyAiResult, AiPredictionOutcome,
 * AiFailureReason). Elles servent UNIQUEMENT à afficher ou masquer : le serveur revérifie tout
 * (403 / 404 / 409). À mettre à jour si les règles du serveur changent.
 *
 * Champs reçus (claim-service, étape 7c) :
 * - tous les rôles : autoClassified (booléen : la catégorie actuelle vient de l'IA) ;
 * - agents et admins seulement (ABSENTS pour un client) : classificationSource, confidenceScore,
 *   aiPrediction = { outcome, failureReason, predictedCategory, predictedService, confidence,
 *   modelVersion, threshold, predictedAt, decision, finalCategory, finalService, decidedByRole,
 *   decidedByName, decidedAt }.
 */

const STAFF = ['AGENT', 'ADMIN'];

/** Statuts où la classification peut encore être confirmée ou corrigée. */
const REVIEWABLE_STATUSES = ['CLASSIFIED', 'ASSIGNED', 'IN_PROGRESS'];

/**
 * Seuil de REPLI, utilisé seulement quand aiPrediction.threshold est absent (prédiction antérieure
 * à l'étape 7c du claim-service, ou service IA indisponible). Valeur du modèle v1.0.0 ; le panneau
 * signale alors « valeur par défaut ». Le seuil réel vient toujours de l'API quand il est connu.
 */
export const DEFAULT_THRESHOLD = 0.57;

/** Au-delà, confiance « élevée » (choix d'affichage, indépendant du modèle). */
export const HIGH_CONFIDENCE = 0.85;

/** Seuil à utiliser pour une prédiction : celui renvoyé par l'API, sinon la valeur de repli. */
export function thresholdOf(prediction) {
  const value = prediction?.threshold;
  const known = typeof value === 'number' && value >= 0 && value <= 1;
  return { value: known ? value : DEFAULT_THRESHOLD, fromApi: known };
}

/** Classement automatique appliqué, pas encore confirmé ni corrigé : boutons Confirmer / Corriger. */
export function canReviewAiClassification(claim, user) {
  const prediction = claim?.aiPrediction;
  return STAFF.includes(user?.role)
    && isAiClassified(claim)
    && prediction?.outcome === 'APPLIED'
    && !prediction.decision
    && REVIEWABLE_STATUSES.includes(claim.status);
}

/**
 * Badge « IA » / « Classée automatiquement » : la catégorie actuelle vient du service IA.
 * Basé sur autoClassified, envoyé à TOUS les rôles (classificationSource n'est plus envoyé au client).
 */
export function isAiClassified(claim) {
  return claim?.autoClassified === true;
}

/**
 * Confiance en pourcentage, TRONQUÉE comme côté serveur (ClaimService.percent) : 0,5695 → « 56 % »,
 * jamais plus que la valeur réelle. La marge 1e-9 corrige les flottants (0,57 × 100 = 56,999…).
 */
export function formatConfidence(value) {
  if (value == null || Number.isNaN(Number(value))) return '—';
  return `${Math.floor(Number(value) * 100 + 1e-9)} %`;
}

/**
 * Niveau de confiance : libellé et couleurs (badge et barre).
 * @param threshold seuil du modèle (thresholdOf(prediction).value) : en dessous, « faible »
 */
export function confidenceLevel(value, threshold = DEFAULT_THRESHOLD) {
  if (value == null) return null;
  // Le seuil du modèle passe en premier : sous le seuil, la confiance est toujours « faible »,
  // même au-delà de HIGH_CONFIDENCE (ex. modèle exigeant, seuil 0,90 : 88 % reste faible).
  if (value < threshold) {
    return {
      label: 'Confiance faible',
      badge: 'bg-red-100 dark:bg-red-900/50 text-red-800 dark:text-red-200',
      bar: 'bg-red-500 dark:bg-red-400',
    };
  }
  if (value >= Math.max(HIGH_CONFIDENCE, threshold)) {
    return {
      label: 'Confiance élevée',
      badge: 'bg-emerald-100 dark:bg-emerald-900/50 text-emerald-800 dark:text-emerald-200',
      bar: 'bg-emerald-500 dark:bg-emerald-400',
    };
  }
  return {
    label: 'Confiance moyenne',
    badge: 'bg-amber-100 dark:bg-amber-900/50 text-amber-800 dark:text-amber-200',
    bar: 'bg-amber-500 dark:bg-amber-400',
  };
}

/** Pourquoi le classement automatique n'a pas été appliqué (sans détail technique inutile). */
export const NOT_APPLIED_TEXTS = {
  LOW_CONFIDENCE: 'Confiance trop faible : la proposition de l’IA n’a pas été retenue.',
  UNKNOWN_CATEGORY: 'La catégorie proposée par l’IA n’existe pas dans le référentiel.',
  UNAVAILABLE: 'Le service IA était indisponible lors de la soumission.',
};

/** Repli quand le nom de l'auteur est absent (admin : nom inconnu du claim-service). */
export const DECIDED_BY_LABELS = {
  AGENT: 'un agent',
  ADMIN: 'un administrateur',
};

/** Auteur d'une décision : son nom (decidedByName) si connu, sinon son rôle. Jamais d'e-mail. */
export function decidedByLabel(prediction) {
  const name = prediction?.decidedByName?.trim();
  if (name) return name;
  return DECIDED_BY_LABELS[prediction?.decidedByRole] ?? null;
}

export const DECISION_LABELS = {
  CONFIRMED: 'Confirmée',
  CORRECTED: 'Corrigée',
};

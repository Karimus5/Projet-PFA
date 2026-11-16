import { useState } from 'react';
import { claimsApi } from '../../api/claimsApi';
import {
  DECIDED_BY_LABELS,
  DECISION_LABELS,
  NOT_APPLIED_TEXTS,
  canReviewAiClassification,
  confidenceLevel,
  formatConfidence,
} from '../../claims/aiRules';
import { formatDateTime } from '../../utils/format';
import { Spinner } from '../PageLoader';
import AiBadge from './AiBadge';
import ClassificationPanel from './ClassificationPanel';

/**
 * Panneau « Classification » (agents et admins uniquement, BF14, BF18, BF19) : source du classement,
 * proposition de l'IA (catégorie, service, confiance, version du modèle), décision éventuelle.
 * Boutons Confirmer / Corriger seulement sur un classement IA pas encore décidé ; Corriger ouvre le
 * formulaire de classification existant. Jamais affiché à un client (aiPrediction est null pour lui).
 */
export default function AiClassificationPanel({ claim, user, categories, services, onPerform }) {
  const prediction = claim.aiPrediction;
  const reviewable = canReviewAiClassification(claim, user);
  const [correcting, setCorrecting] = useState(false);
  const [confirming, setConfirming] = useState(false);

  async function confirm() {
    setConfirming(true);
    await onPerform(() => claimsApi.confirmClassification(claim.id), 'Classification confirmée.');
    setConfirming(false);
  }

  return (
    <>
      <section className="card" aria-labelledby="classification-ia-titre">
        <div className="flex items-center justify-between gap-2">
          <h2 id="classification-ia-titre" className="text-lg font-medium text-slate-900 dark:text-slate-100">Classification</h2>
          {claim.classificationSource === 'AI' && <AiBadge />}
        </div>

        <dl className="mt-3 space-y-2 text-sm">
          <Row label="Source" value={sourceLabel(claim)} />
          {prediction && (prediction.predictedCategory || prediction.confidence != null) && (
            <>
              <Row label="Proposition de l’IA" value={proposal(prediction)} />
              {prediction.confidence != null && <ConfidenceRow value={prediction.confidence} />}
            </>
          )}
          {prediction?.modelVersion && <Row label="Modèle" value={`v${prediction.modelVersion}`} />}
        </dl>

        {prediction && prediction.outcome !== 'APPLIED' && (
          <p className="mt-3 rounded-md bg-slate-50 dark:bg-slate-800/60 px-3 py-2 text-sm text-slate-700 dark:text-slate-300">
            Classement automatique non appliqué. {NOT_APPLIED_TEXTS[prediction.outcome] ?? ''}
            {claim.status === 'NEW' && ' La réclamation est à classer manuellement.'}
          </p>
        )}

        {prediction?.decision && (
          <p className="mt-3 rounded-md bg-slate-50 dark:bg-slate-800/60 px-3 py-2 text-sm text-slate-700 dark:text-slate-300">
            <span className="font-medium">{DECISION_LABELS[prediction.decision] ?? prediction.decision}</span>
            {prediction.decidedByRole && ` par ${DECIDED_BY_LABELS[prediction.decidedByRole] ?? prediction.decidedByRole}`}
            {prediction.decidedAt && ` le ${formatDateTime(prediction.decidedAt)}`}
            {prediction.decision === 'CORRECTED' && prediction.finalCategory
              && ` : ${prediction.finalCategory} (service ${prediction.finalService ?? '—'})`}
          </p>
        )}

        {reviewable && !correcting && (
          <div className="mt-4 flex flex-wrap gap-2">
            <button type="button" className="btn-success" onClick={confirm} disabled={confirming}>
              {confirming && <Spinner className="h-4 w-4" />}
              Confirmer
            </button>
            <button type="button" className="btn-secondary" onClick={() => setCorrecting(true)} disabled={confirming}>
              Corriger
            </button>
          </div>
        )}
        {reviewable && !correcting && (
          <p className="mt-2 text-xs text-slate-500 dark:text-slate-400">
            Confirmer ou corriger la proposition aide à améliorer le modèle (retour d’information enregistré).
          </p>
        )}
      </section>

      {reviewable && correcting && (
        <ClassificationPanel
          claim={claim}
          user={user}
          categories={categories}
          services={services}
          onPerform={onPerform}
          onCancel={() => setCorrecting(false)}
        />
      )}
    </>
  );
}

function sourceLabel(claim) {
  if (claim.classificationSource === 'AI') return 'Classement automatique (IA)';
  if (claim.classificationSource === 'MANUAL') return 'Classement manuel';
  return 'Non classée';
}

function proposal(prediction) {
  if (!prediction.predictedCategory) return 'Aucune catégorie';
  return prediction.predictedService && prediction.predictedService !== prediction.predictedCategory
    ? `${prediction.predictedCategory} (service ${prediction.predictedService})`
    : prediction.predictedCategory;
}

function Row({ label, value }) {
  return (
    <div className="flex justify-between gap-3">
      <dt className="text-slate-500 dark:text-slate-400">{label}</dt>
      <dd className="text-right font-medium text-slate-800 dark:text-slate-200">{value}</dd>
    </div>
  );
}

/** Confiance : pourcentage, badge de niveau et barre (BF18). */
function ConfidenceRow({ value }) {
  const level = confidenceLevel(value);
  const width = Math.max(0, Math.min(100, Math.floor(value * 100)));
  return (
    <div>
      <div className="flex items-center justify-between gap-3">
        <dt className="text-slate-500 dark:text-slate-400">Confiance</dt>
        <dd className="flex items-center gap-2 font-medium text-slate-800 dark:text-slate-200">
          <span className={`rounded px-1.5 py-0.5 text-xs font-medium ${level.badge}`}>{level.label}</span>
          {formatConfidence(value)}
        </dd>
      </div>
      <div
        className="mt-1.5 h-2 w-full overflow-hidden rounded-full bg-slate-200 dark:bg-slate-700"
        role="meter"
        aria-label="Confiance de l’IA"
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={width}
      >
        <div className={`h-full rounded-full ${level.bar}`} style={{ width: `${width}%` }} />
      </div>
    </div>
  );
}

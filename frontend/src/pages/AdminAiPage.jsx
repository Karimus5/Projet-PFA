import { useCallback, useEffect, useState } from 'react';
import { claimsApi } from '../api/claimsApi';
import { saveBlob } from '../claims/attachmentRules';
import { describeError } from '../claims/errors';
import Alert from '../components/Alert';
import PageLoader, { Spinner } from '../components/PageLoader';

/** Taux (0..1) en pourcentage lisible, une décimale : 0.875 → « 87,5 % » ; null → « — ». */
export function formatRate(value) {
  if (value == null || Number.isNaN(Number(value))) return '—';
  return `${new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 1 }).format(Number(value) * 100)} %`;
}

/**
 * Page « Intelligence artificielle » (ADMIN) : indicateurs du classement automatique
 * (GET /api/claims/admin/ai-stats, BF25) et export CSV des confirmations / corrections des agents
 * pour réentraîner le modèle (GET /api/claims/admin/ai-feedback/export, BF20). Le CSV est
 * téléchargé par un appel authentifié (jeton dans l'en-tête, jamais dans une URL).
 */
export default function AdminAiPage() {
  const [stats, setStats] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [exporting, setExporting] = useState(false);
  const [exportFeedback, setExportFeedback] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setStats(await claimsApi.aiStats());
    } catch (err) {
      setError(describeError(err));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  async function exportCsv() {
    setExporting(true);
    setExportFeedback(null);
    try {
      const blob = await claimsApi.exportAiFeedback();
      saveBlob(blob, `ai-feedback-${new Date().toISOString().slice(0, 10)}.csv`);
      setExportFeedback({ type: 'success', text: 'Export téléchargé.' });
    } catch (err) {
      setExportFeedback({ type: 'error', text: `Export impossible : ${describeError(err)}` });
    } finally {
      setExporting(false);
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900 dark:text-slate-100">Intelligence artificielle</h1>
          <p className="mt-1 max-w-2xl text-sm text-slate-600 dark:text-slate-400">
            Suivi du classement automatique des réclamations : ce que l’IA classe seule, ce que les agents
            confirment ou corrigent, et ce qui part en classement manuel.
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <button type="button" className="btn-secondary" onClick={load} disabled={loading}>
            Actualiser
          </button>
          <button type="button" className="btn-primary" onClick={exportCsv} disabled={exporting}>
            {exporting && <Spinner className="h-4 w-4" />}
            Exporter les corrections (CSV)
          </button>
        </div>
      </div>

      {exportFeedback && (
        <Alert type={exportFeedback.type} onClose={() => setExportFeedback(null)}>{exportFeedback.text}</Alert>
      )}

      {loading && !stats ? (
        <PageLoader label="Chargement des indicateurs…" />
      ) : error ? (
        <div className="space-y-3">
          <Alert type="error">Indicateurs indisponibles : {error}</Alert>
          <button type="button" className="btn-secondary" onClick={load}>Réessayer</button>
        </div>
      ) : stats && stats.predictions === 0 ? (
        <div className="card">
          <p className="text-sm text-slate-600 dark:text-slate-400">
            Aucune donnée pour l’instant : aucune réclamation n’a encore été soumise au service IA.
            Les indicateurs apparaîtront après les premières soumissions.
          </p>
        </div>
      ) : stats ? (
        <Indicators stats={stats} />
      ) : null}

      <section className="card text-sm text-slate-600 dark:text-slate-400" aria-labelledby="export-titre">
        <h2 id="export-titre" className="text-base font-medium text-slate-900 dark:text-slate-100">À propos de l’export</h2>
        <p className="mt-2">
          Une ligne par réclamation confirmée ou corrigée : titre, description, catégorie et service proposés,
          confiance, catégorie et service retenus, décision, version du modèle, rôle de l’auteur et date. Aucune
          adresse e-mail ni identifiant de client ou d’agent. Ce fichier sert à réentraîner le modèle.
        </p>
      </section>
    </div>
  );
}

function Indicators({ stats }) {
  return (
    <div className="space-y-6">
      <section aria-labelledby="volume-titre">
        <h2 id="volume-titre" className="sr-only">Volume</h2>
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <StatCard label="Prédictions" value={stats.predictions}
            help="Réclamations soumises au service IA lors de leur création." />
          <StatCard label="Classées automatiquement" value={stats.autoClassified}
            help="Confiance suffisante : catégorie et service renseignés sans intervention." />
          <StatCard label="Envoyées en classement manuel" value={stats.sentToManual}
            help="Confiance trop faible, catégorie inconnue ou service IA indisponible." />
        </div>
      </section>

      <section aria-labelledby="retours-titre">
        <h2 id="retours-titre" className="mb-3 text-lg font-medium text-slate-900 dark:text-slate-100">
          Retours des agents
        </h2>
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <StatCard label="Confirmées" value={stats.confirmed}
            help="Propositions de l’IA validées telles quelles par un agent ou un administrateur." />
          <StatCard label="Corrigées" value={stats.corrected}
            help="Propositions dont la catégorie ou le service a été changé." />
          <StatCard label="En attente de vérification" value={stats.awaitingReview}
            help="Classées automatiquement, ni confirmées ni corrigées pour l’instant." />
        </div>
      </section>

      <section aria-labelledby="taux-titre">
        <h2 id="taux-titre" className="mb-3 text-lg font-medium text-slate-900 dark:text-slate-100">Taux</h2>
        <div className="grid gap-4 sm:grid-cols-2">
          <StatCard label="Taux de classement automatique" value={formatRate(stats.autoClassificationRate)}
            help="Part des prédictions appliquées automatiquement (classées automatiquement ÷ prédictions)."
            rate={stats.autoClassificationRate} />
          <StatCard label="Taux de correction" value={formatRate(stats.correctionRate)}
            help={stats.correctionRate == null
              ? 'Aucune confirmation ni correction pour l’instant.'
              : 'Part des propositions vérifiées qui ont été corrigées (corrigées ÷ (confirmées + corrigées)).'}
            rate={stats.correctionRate} />
        </div>
      </section>

      <section aria-labelledby="causes-titre">
        <h2 id="causes-titre" className="mb-3 text-lg font-medium text-slate-900 dark:text-slate-100">
          Causes du classement manuel
        </h2>
        <div className="grid gap-4 sm:grid-cols-3">
          <StatCard label="Confiance trop faible" value={stats.lowConfidence}
            help="L’IA a hésité : sa proposition n’a pas été retenue." />
          <StatCard label="Catégorie inconnue" value={stats.unknownCategory}
            help="La catégorie proposée n’existe pas dans les référentiels." />
          <StatCard label="Service IA indisponible" value={stats.unavailable}
            help="Service arrêté, trop lent ou en erreur au moment de la soumission." />
        </div>
      </section>
    </div>
  );
}

function StatCard({ label, value, help, rate }) {
  const width = rate == null ? null : Math.max(0, Math.min(100, Number(rate) * 100));
  return (
    <div className="card">
      <p className="text-sm text-slate-500 dark:text-slate-400">{label}</p>
      <p className="mt-1 text-3xl font-semibold text-slate-900 dark:text-slate-100">{value ?? '—'}</p>
      {width != null && (
        <div className="mt-2 h-1.5 w-full overflow-hidden rounded-full bg-slate-200 dark:bg-slate-700" aria-hidden="true">
          <div className="h-full rounded-full bg-indigo-500 dark:bg-indigo-400" style={{ width: `${width}%` }} />
        </div>
      )}
      <p className="mt-2 text-xs text-slate-500 dark:text-slate-400">{help}</p>
    </div>
  );
}

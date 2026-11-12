import { useCallback, useEffect, useState } from 'react';
import { Link, useLocation, useParams } from 'react-router';
import { claimsApi } from '../api/claimsApi';
import { useAuth } from '../auth/AuthContext';
import {
  canAddComplement,
  canAssign,
  canClassifyNew,
  canCorrectClassification,
} from '../claims/claimRules';
import { describeError } from '../claims/errors';
import Alert from '../components/Alert';
import PageLoader from '../components/PageLoader';
import AssignmentPanel from '../components/claims/AssignmentPanel';
import AttachmentsSection from '../components/claims/AttachmentsSection';
import ClassificationPanel from '../components/claims/ClassificationPanel';
import CommentsSection from '../components/claims/CommentsSection';
import ComplementForm from '../components/claims/ComplementForm';
import HistoryTimeline from '../components/claims/HistoryTimeline';
import StatusActions from '../components/claims/StatusActions';
import StatusBadge from '../components/claims/StatusBadge';
import { formatDateTime } from '../utils/format';

/** Une réponse est un ClaimDetail complet (statut, classement, affectation, complément). */
const isClaimDetail = (value) => value && typeof value === 'object' && Array.isArray(value.history);

/**
 * Détail d'une réclamation (BF8) pour les trois rôles. Les panneaux d'action ne sont affichés
 * que si le rôle et le statut le permettent (claimRules.js) ; le serveur revérifie tout.
 */
export default function ClaimDetailPage() {
  const { id } = useParams();
  const { user } = useAuth();
  const location = useLocation();
  const staff = user.role !== 'CLIENT';
  const validId = /^\d+$/.test(id ?? '');

  const [claim, setClaim] = useState(null);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(!validId);
  const [loadError, setLoadError] = useState(null);
  const [feedback, setFeedback] = useState(location.state?.notice ?? null);
  const [references, setReferences] = useState({ categories: [], services: [], error: null });

  const reload = useCallback(async () => {
    if (!validId) return;
    try {
      setClaim(await claimsApi.get(id));
      setNotFound(false);
      setLoadError(null);
    } catch (err) {
      if (err.status === 404) setNotFound(true);
      else setLoadError(describeError(err));
    } finally {
      setLoading(false);
    }
  }, [id, validId]);

  useEffect(() => {
    reload();
  }, [reload]);

  // Référentiels (catégories, services) : lisibles par les agents et les admins seulement.
  useEffect(() => {
    if (!staff) return undefined;
    let cancelled = false;
    Promise.all([claimsApi.listCategories(), claimsApi.listServices()])
      .then(([categories, services]) => !cancelled && setReferences({ categories, services, error: null }))
      .catch((err) => !cancelled && setReferences({ categories: [], services: [], error: describeError(err) }));
    return () => {
      cancelled = true;
    };
  }, [staff]);

  /**
   * Exécute une action et gère le retour de façon uniforme : succès → réclamation mise à jour ;
   * échec → message en français, et rechargement si l'état a pu changer (409) ou si la
   * réclamation n'est plus visible (404). Renvoie l'erreur (pour les erreurs par champ) ou null.
   */
  const perform = useCallback(
    async (action, successText) => {
      setFeedback(null);
      try {
        const result = await action();
        if (isClaimDetail(result)) setClaim(result);
        else await reload();
        setFeedback({ type: 'success', text: successText });
        return null;
      } catch (err) {
        setFeedback({ type: 'error', text: describeError(err) });
        if (err.status === 409 || err.status === 404) await reload();
        return err;
      }
    },
    [reload],
  );

  if (loading && validId) return <PageLoader label="Chargement de la réclamation…" />;

  if (notFound) {
    return (
      <div className="mx-auto max-w-lg py-12 text-center">
        <h1 className="text-2xl font-semibold text-slate-900">Réclamation introuvable</h1>
        <p className="mt-2 text-slate-600">
          Cette réclamation n’existe pas ou vous n’y avez pas accès.
        </p>
        {feedback?.type === 'success' && <p className="mt-2 text-sm text-slate-500">{feedback.text}</p>}
        <Link to="/reclamations" className="btn-primary mt-6">Retour à la liste</Link>
      </div>
    );
  }

  if (loadError) {
    return (
      <div className="space-y-4">
        <Alert type="error">{loadError}</Alert>
        <button type="button" className="btn-secondary" onClick={reload}>Réessayer</button>
      </div>
    );
  }

  const panelKey = `${claim.id}-${claim.updatedAt}-${claim.status}`;
  const showClassification = staff && (canClassifyNew(claim, user) || canCorrectClassification(claim, user));
  const showAssignment = canAssign(claim, user);

  return (
    <div className="space-y-6">
      <div>
        <Link to="/reclamations" className="text-sm text-indigo-700 hover:underline">← Retour à la liste</Link>
        <div className="mt-2 flex flex-wrap items-center gap-3">
          <h1 className="text-2xl font-semibold text-slate-900">{claim.title}</h1>
          <StatusBadge status={claim.status} />
        </div>
        <p className="mt-1 text-sm text-slate-500">
          Réclamation n° {claim.id} · créée le {formatDateTime(claim.createdAt)} · mise à jour le{' '}
          {formatDateTime(claim.updatedAt)}
        </p>
      </div>

      {feedback && (
        <Alert type={feedback.type} onClose={() => setFeedback(null)}>
          {feedback.text}
        </Alert>
      )}

      <div className="grid gap-6 lg:grid-cols-3">
        <div className="space-y-6 lg:col-span-2">
          <section className="card" aria-labelledby="description-titre">
            <h2 id="description-titre" className="text-lg font-medium text-slate-900">Description</h2>
            <p className="mt-2 whitespace-pre-line text-sm text-slate-800">{claim.description}</p>
            {canAddComplement(claim, user) && <ComplementForm key={panelKey} claimId={claim.id} onPerform={perform} />}
          </section>

          <CommentsSection claim={claim} user={user} onPerform={perform} />
          <AttachmentsSection claim={claim} onPerform={perform} />
        </div>

        <div className="space-y-6">
          <section className="card" aria-labelledby="infos-titre">
            <h2 id="infos-titre" className="text-lg font-medium text-slate-900">Informations</h2>
            <dl className="mt-3 space-y-2 text-sm">
              <Info label="Catégorie" value={claim.category?.name ?? 'Non classée'} />
              <Info label="Service" value={claim.service?.name ?? 'Non attribué'} />
              {staff && (
                <Info
                  label="Agent"
                  value={claim.assignedAgent ? `${claim.assignedAgent.name}${claim.assignedAgent.id === user.id ? ' (vous)' : ''}` : 'Non affectée'}
                />
              )}
              {staff && <Info label="Client" value={claim.clientEmail} />}
              {staff && claim.classificationSource && (
                <Info
                  label="Classement"
                  value={`${claim.classificationSource === 'AI' ? 'IA' : 'Manuel'}${
                    claim.confidenceScore != null ? ` (confiance ${Math.round(claim.confidenceScore * 100)} %)` : ''
                  }`}
                />
              )}
            </dl>
          </section>

          <StatusActions key={`statut-${panelKey}`} claim={claim} user={user} onPerform={perform} />

          {references.error && (showClassification || showAssignment) && (
            <Alert type="error">Référentiels indisponibles : {references.error}</Alert>
          )}
          {showClassification && (
            <ClassificationPanel
              key={`classement-${panelKey}`}
              claim={claim}
              user={user}
              categories={references.categories}
              services={references.services}
              onPerform={perform}
            />
          )}
          {showAssignment && (
            <AssignmentPanel key={`affectation-${panelKey}`} claim={claim} services={references.services} onPerform={perform} />
          )}

          <section className="card" aria-labelledby="historique-titre">
            <h2 id="historique-titre" className="text-lg font-medium text-slate-900">Historique</h2>
            <div className="mt-3">
              <HistoryTimeline history={claim.history} currentUser={user} />
            </div>
          </section>
        </div>
      </div>
    </div>
  );
}

function Info({ label, value }) {
  return (
    <div className="flex justify-between gap-3">
      <dt className="text-slate-500">{label}</dt>
      <dd className="text-right font-medium text-slate-800">{value}</dd>
    </div>
  );
}

import { useCallback, useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { claimsApi } from '../api/claimsApi';
import { useAuth } from '../auth/AuthContext';
import { STATUSES, STATUS_LABELS } from '../claims/claimRules';
import { describeError } from '../claims/errors';
import Alert from '../components/Alert';
import PageLoader from '../components/PageLoader';
import Pagination from '../components/claims/Pagination';
import StatusBadge from '../components/claims/StatusBadge';
import { formatDateTime } from '../utils/format';

const PAGE_SIZE = 10;

const TEXTS = {
  CLIENT: {
    title: 'Mes réclamations',
    intro: 'Suivez l’avancement de vos réclamations et consultez les réponses des agents.',
    empty: 'Vous n’avez encore soumis aucune réclamation.',
  },
  AGENT: {
    title: 'Réclamations du service',
    intro: 'Réclamations de vos services et celles qui vous sont affectées.',
    empty: 'Aucune réclamation à traiter.',
  },
  ADMIN: {
    title: 'Toutes les réclamations',
    intro: 'Classez et affectez les nouvelles réclamations, suivez l’ensemble du traitement.',
    empty: 'Aucune réclamation.',
  },
};

/**
 * Liste paginée des réclamations visibles par l'utilisateur (BF7, BF11, BF23), filtrable par statut.
 * Filtre et page sont dans l'URL (?statut=...&page=...) : le bouton Retour du navigateur les conserve.
 */
export default function ClaimsListPage() {
  const { user } = useAuth();
  const texts = TEXTS[user.role] ?? TEXTS.CLIENT;
  const [searchParams, setSearchParams] = useSearchParams();
  const status = STATUSES.includes(searchParams.get('statut')) ? searchParams.get('statut') : '';
  const page = Math.max(0, Number.parseInt(searchParams.get('page') ?? '0', 10) || 0);

  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setData(await claimsApi.list({ status: status || undefined, page, size: PAGE_SIZE }));
    } catch (err) {
      setError(describeError(err));
    } finally {
      setLoading(false);
    }
  }, [status, page]);

  useEffect(() => {
    load();
  }, [load]);

  function updateParams(nextStatus, nextPage) {
    const params = {};
    if (nextStatus) params.statut = nextStatus;
    if (nextPage > 0) params.page = String(nextPage);
    setSearchParams(params);
  }

  const staff = user.role !== 'CLIENT';
  const claims = data?.content ?? [];

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">{texts.title}</h1>
          <p className="mt-1 text-sm text-slate-600">{texts.intro}</p>
        </div>
        {user.role === 'CLIENT' && (
          <Link to="/reclamations/nouvelle" className="btn-primary">
            Nouvelle réclamation
          </Link>
        )}
      </div>

      <div className="card flex flex-wrap items-end gap-4 py-4">
        <div className="w-full sm:w-64">
          <label htmlFor="filtre-statut" className="mb-1 block text-sm font-medium text-slate-700">
            Filtrer par statut
          </label>
          <select id="filtre-statut" className="input" value={status} onChange={(e) => updateParams(e.target.value, 0)}>
            <option value="">Tous les statuts</option>
            {STATUSES.map((value) => (
              <option key={value} value={value}>{STATUS_LABELS[value]}</option>
            ))}
          </select>
        </div>
        {user.role === 'ADMIN' && status !== 'NEW' && (
          <button type="button" className="btn-secondary" onClick={() => updateParams('NEW', 0)}>
            Nouvelles à classer
          </button>
        )}
        <button type="button" className="btn-secondary" onClick={load} disabled={loading}>
          Actualiser
        </button>
      </div>

      <section className="card p-0" aria-label="Liste des réclamations">
        {loading && !data ? (
          <PageLoader label="Chargement des réclamations…" />
        ) : error ? (
          <div className="p-6">
            <Alert type="error">{error}</Alert>
          </div>
        ) : claims.length === 0 ? (
          <p className="p-6 text-sm text-slate-500">
            {status ? `Aucune réclamation au statut « ${STATUS_LABELS[status]} ».` : texts.empty}
          </p>
        ) : (
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-slate-200 text-sm">
              <thead className="bg-slate-50 text-left text-xs font-medium uppercase tracking-wide text-slate-500">
                <tr>
                  <th scope="col" className="px-4 py-3">N°</th>
                  <th scope="col" className="px-4 py-3">Titre</th>
                  <th scope="col" className="px-4 py-3">Statut</th>
                  <th scope="col" className="px-4 py-3">Catégorie</th>
                  <th scope="col" className="px-4 py-3">Service</th>
                  {staff && <th scope="col" className="px-4 py-3">Agent</th>}
                  <th scope="col" className="px-4 py-3">Mise à jour</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {claims.map((claim) => (
                  <tr key={claim.id} className="hover:bg-slate-50">
                    <td className="whitespace-nowrap px-4 py-3 text-slate-500">#{claim.id}</td>
                    <td className="px-4 py-3">
                      <Link to={`/reclamations/${claim.id}`} className="font-medium text-indigo-700 hover:underline">
                        {claim.title}
                      </Link>
                    </td>
                    <td className="px-4 py-3"><StatusBadge status={claim.status} /></td>
                    <td className="whitespace-nowrap px-4 py-3 text-slate-700">{claim.category?.name ?? '—'}</td>
                    <td className="whitespace-nowrap px-4 py-3 text-slate-700">{claim.service?.name ?? '—'}</td>
                    {staff && (
                      <td className="whitespace-nowrap px-4 py-3 text-slate-700">
                        {claim.assignedAgent ? (
                          <>
                            {claim.assignedAgent.name}
                            {claim.assignedAgent.id === user.id && (
                              <span className="ml-1 rounded bg-indigo-100 px-1.5 py-0.5 text-xs text-indigo-800">vous</span>
                            )}
                          </>
                        ) : (
                          <span className="text-slate-400">Non affectée</span>
                        )}
                      </td>
                    )}
                    <td className="whitespace-nowrap px-4 py-3 text-slate-600">{formatDateTime(claim.updatedAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {data && !error && (
        <Pagination
          page={data.page}
          totalPages={data.totalPages}
          totalElements={data.totalElements}
          disabled={loading}
          onChange={(next) => updateParams(status, next)}
        />
      )}
    </div>
  );
}

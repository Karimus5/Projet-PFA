import { useEffect, useState } from 'react';
import { claimsApi } from '../../api/claimsApi';
import { agentRequiredForAssignment } from '../../claims/claimRules';
import { describeError } from '../../claims/errors';
import { Spinner } from '../PageLoader';

/**
 * Affectation (CLASSIFIED : agent facultatif, sinon la réclamation reste dans la file du service)
 * ou réaffectation (ASSIGNED / IN_PROGRESS : agent obligatoire, statut conservé ; BF15, BF23).
 * Les agents proposés sont ceux rattachés au service choisi (GET /api/claims/services/{id}/agents).
 */
export default function AssignmentPanel({ claim, services, onPerform }) {
  const agentRequired = agentRequiredForAssignment(claim);
  const [serviceId, setServiceId] = useState(claim.service?.id ? String(claim.service.id) : '');
  const [agentId, setAgentId] = useState('');
  const [agents, setAgents] = useState([]);
  const [agentsLoading, setAgentsLoading] = useState(false);
  const [agentsError, setAgentsError] = useState(null);
  const [error, setError] = useState(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    setAgentId('');
    setAgents([]);
    setAgentsError(null);
    if (!serviceId) return undefined;
    let cancelled = false;
    setAgentsLoading(true);
    claimsApi
      .listServiceAgents(serviceId)
      .then((list) => !cancelled && setAgents(list))
      .catch((err) => !cancelled && setAgentsError(describeError(err)))
      .finally(() => !cancelled && setAgentsLoading(false));
    return () => {
      cancelled = true;
    };
  }, [serviceId]);

  async function handleSubmit(event) {
    event.preventDefault();
    if (!serviceId) return setError('Le service est obligatoire');
    if (agentRequired && !agentId) return setError('Choisissez un agent pour réaffecter une réclamation en cours');
    setError(null);
    setSaving(true);
    const err = await onPerform(
      () => claimsApi.assign(claim.id, Number(serviceId), agentId ? Number(agentId) : null),
      agentId ? 'Réclamation affectée.' : 'Réclamation placée dans la file du service.',
    );
    setSaving(false);
    if (err) setError(err.fieldErrors?.serviceId ?? null);
  }

  const reassign = claim.status !== 'CLASSIFIED';
  return (
    <section className="card" aria-labelledby="affectation-titre">
      <h2 id="affectation-titre" className="text-lg font-medium text-slate-900">
        {reassign ? 'Réaffecter' : 'Affecter à un agent'}
      </h2>
      <p className="mt-1 text-xs text-slate-500">
        {reassign
          ? 'Le statut est conservé ; un agent du service choisi est obligatoire.'
          : 'Avec un agent, la réclamation passe à « Affectée ». Sans agent, elle reste dans la file du service.'}
      </p>
      <form className="mt-3 space-y-3" onSubmit={handleSubmit} noValidate>
        <div>
          <label htmlFor="affectation-service" className="mb-1 block text-sm font-medium text-slate-700">Service</label>
          <select id="affectation-service" className="input" value={serviceId} onChange={(e) => setServiceId(e.target.value)}>
            <option value="">— Choisir un service —</option>
            {services.map((service) => (
              <option key={service.id} value={service.id}>{service.name}</option>
            ))}
          </select>
        </div>
        <div>
          <label htmlFor="affectation-agent" className="mb-1 block text-sm font-medium text-slate-700">Agent</label>
          <select
            id="affectation-agent"
            className="input"
            value={agentId}
            onChange={(e) => setAgentId(e.target.value)}
            disabled={!serviceId || agentsLoading}
          >
            <option value="">{agentRequired ? '— Choisir un agent —' : 'Aucun (file du service)'}</option>
            {agents.map((agent) => (
              <option key={agent.agentId} value={agent.agentId}>
                {agent.agentName}
                {claim.assignedAgent?.id === agent.agentId ? ' (actuel)' : ''}
              </option>
            ))}
          </select>
          {agentsError && <p className="mt-1 text-sm text-red-600">{agentsError}</p>}
          {serviceId && !agentsLoading && !agentsError && agents.length === 0 && (
            <p className="mt-1 text-xs text-amber-700">
              Aucun agent n'est rattaché à ce service (rattachement : page Référentiels, administrateur).
            </p>
          )}
        </div>
        {error && <p role="alert" className="text-sm text-red-600">{error}</p>}
        <button type="submit" className="btn-primary" disabled={saving || !serviceId}>
          {saving && <Spinner className="h-4 w-4" />}
          {reassign ? 'Réaffecter' : 'Affecter'}
        </button>
      </form>
    </section>
  );
}

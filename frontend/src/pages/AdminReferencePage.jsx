import { useCallback, useEffect, useState } from 'react';
import { authApi } from '../api/authApi';
import { claimsApi } from '../api/claimsApi';
import { describeError } from '../claims/errors';
import Alert from '../components/Alert';
import FormField from '../components/FormField';
import PageLoader, { Spinner } from '../components/PageLoader';
import { compact, validateRefDescription, validateRefName } from '../utils/validation';

/**
 * BF22 : gestion des services et des catégories (CRUD) et rattachement des agents aux services
 * (l'agent est choisi parmi les comptes AGENT actifs d'auth-service). Réservé au rôle ADMIN.
 */
export default function AdminReferencePage() {
  const [services, setServices] = useState([]);
  const [categories, setCategories] = useState([]);
  const [agents, setAgents] = useState([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(null);
  const [feedback, setFeedback] = useState(null);

  const load = useCallback(async () => {
    setLoadError(null);
    try {
      const [serviceList, categoryList, users] = await Promise.all([
        claimsApi.listServices(),
        claimsApi.listCategories(),
        authApi.listUsers(),
      ]);
      setServices(serviceList);
      setCategories(categoryList);
      setAgents(users.filter((u) => u.role === 'AGENT'));
    } catch (err) {
      setLoadError(describeError(err));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  /** Exécute une écriture, affiche le résultat et recharge les listes. Renvoie l'erreur ou null. */
  const perform = useCallback(
    async (action, successText) => {
      setFeedback(null);
      try {
        await action();
        setFeedback({ type: 'success', text: successText });
        await load();
        return null;
      } catch (err) {
        setFeedback({ type: 'error', text: describeError(err) });
        return err;
      }
    },
    [load],
  );

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Référentiels</h1>
        <p className="mt-1 text-sm text-slate-600">
          Services de traitement, catégories de réclamations et agents rattachés à chaque service.
        </p>
      </div>

      {feedback && (
        <Alert type={feedback.type} onClose={() => setFeedback(null)}>
          {feedback.text}
        </Alert>
      )}

      {loading ? (
        <PageLoader label="Chargement des référentiels…" />
      ) : loadError ? (
        <div className="space-y-3">
          <Alert type="error">{loadError}</Alert>
          <button type="button" className="btn-secondary" onClick={load}>Réessayer</button>
        </div>
      ) : (
        <>
          <ServicesSection services={services} onPerform={perform} />
          <CategoriesSection categories={categories} services={services} onPerform={perform} />
          <ServiceAgentsSection services={services} agents={agents} onPerform={perform} />
        </>
      )}
    </div>
  );
}

// --- Services ----------------------------------------------------------------------------------

function ServicesSection({ services, onPerform }) {
  const [editingId, setEditingId] = useState(null);

  async function remove(service) {
    if (!window.confirm(`Supprimer le service « ${service.name} » ?`)) return;
    await onPerform(() => claimsApi.deleteService(service.id), `Service « ${service.name} » supprimé.`);
  }

  return (
    <section className="card" aria-labelledby="services-titre">
      <h2 id="services-titre" className="text-lg font-medium text-slate-900">Services ({services.length})</h2>
      <p className="mt-1 text-xs text-slate-500">
        Un service qui traite encore des catégories ou des réclamations ne peut pas être supprimé.
      </p>
      <ul className="mt-4 divide-y divide-slate-100">
        {services.map((service) =>
          editingId === service.id ? (
            <li key={service.id} className="py-3">
              <RefForm
                idPrefix={`service-${service.id}`}
                initial={{ name: service.name, description: service.description ?? '' }}
                submitLabel="Enregistrer"
                onCancel={() => setEditingId(null)}
                onSubmit={async (data) => {
                  const err = await onPerform(() => claimsApi.updateService(service.id, data), 'Service modifié.');
                  if (!err) setEditingId(null);
                  return err;
                }}
              />
            </li>
          ) : (
            <li key={service.id} className="flex items-center justify-between gap-3 py-3">
              <div className="min-w-0 text-sm">
                <p className="font-medium text-slate-900">{service.name}</p>
                {service.description && <p className="text-slate-500">{service.description}</p>}
              </div>
              <div className="flex shrink-0 gap-2">
                <button type="button" className="btn-secondary py-1" onClick={() => setEditingId(service.id)}
                  aria-label={`Modifier le service ${service.name}`}>
                  Modifier
                </button>
                <button type="button" className="btn-danger py-1" onClick={() => remove(service)}
                  aria-label={`Supprimer le service ${service.name}`}>
                  Supprimer
                </button>
              </div>
            </li>
          ),
        )}
      </ul>
      <div className="mt-4 border-t border-slate-200 pt-4">
        <h3 className="mb-2 text-sm font-medium text-slate-700">Nouveau service</h3>
        <RefForm
          idPrefix="nouveau-service"
          initial={{ name: '', description: '' }}
          submitLabel="Créer le service"
          resetOnSuccess
          onSubmit={(data) => onPerform(() => claimsApi.createService(data), `Service « ${data.name} » créé.`)}
        />
      </div>
    </section>
  );
}

// --- Catégories --------------------------------------------------------------------------------

function CategoriesSection({ categories, services, onPerform }) {
  const [editingId, setEditingId] = useState(null);

  async function remove(category) {
    if (!window.confirm(`Supprimer la catégorie « ${category.name} » ?`)) return;
    await onPerform(() => claimsApi.deleteCategory(category.id), `Catégorie « ${category.name} » supprimée.`);
  }

  return (
    <section className="card" aria-labelledby="categories-titre">
      <h2 id="categories-titre" className="text-lg font-medium text-slate-900">Catégories ({categories.length})</h2>
      <p className="mt-1 text-xs text-slate-500">Chaque catégorie est traitée par un service.</p>
      <ul className="mt-4 divide-y divide-slate-100">
        {categories.map((category) =>
          editingId === category.id ? (
            <li key={category.id} className="py-3">
              <RefForm
                idPrefix={`categorie-${category.id}`}
                initial={{
                  name: category.name,
                  description: category.description ?? '',
                  serviceId: String(category.service?.id ?? ''),
                }}
                services={services}
                submitLabel="Enregistrer"
                onCancel={() => setEditingId(null)}
                onSubmit={async (data) => {
                  const err = await onPerform(() => claimsApi.updateCategory(category.id, data), 'Catégorie modifiée.');
                  if (!err) setEditingId(null);
                  return err;
                }}
              />
            </li>
          ) : (
            <li key={category.id} className="flex items-center justify-between gap-3 py-3">
              <div className="min-w-0 text-sm">
                <p className="font-medium text-slate-900">
                  {category.name} <span className="font-normal text-slate-500">→ {category.service?.name}</span>
                </p>
                {category.description && <p className="text-slate-500">{category.description}</p>}
              </div>
              <div className="flex shrink-0 gap-2">
                <button type="button" className="btn-secondary py-1" onClick={() => setEditingId(category.id)}
                  aria-label={`Modifier la catégorie ${category.name}`}>
                  Modifier
                </button>
                <button type="button" className="btn-danger py-1" onClick={() => remove(category)}
                  aria-label={`Supprimer la catégorie ${category.name}`}>
                  Supprimer
                </button>
              </div>
            </li>
          ),
        )}
      </ul>
      <div className="mt-4 border-t border-slate-200 pt-4">
        <h3 className="mb-2 text-sm font-medium text-slate-700">Nouvelle catégorie</h3>
        <RefForm
          idPrefix="nouvelle-categorie"
          initial={{ name: '', description: '', serviceId: '' }}
          services={services}
          submitLabel="Créer la catégorie"
          resetOnSuccess
          onSubmit={(data) => onPerform(() => claimsApi.createCategory(data), `Catégorie « ${data.name} » créée.`)}
        />
      </div>
    </section>
  );
}

/**
 * Formulaire nom + description (+ service si `services` est fourni, pour une catégorie).
 * onSubmit(data) renvoie l'erreur de l'API (pour les erreurs par champ) ou null.
 */
function RefForm({ idPrefix, initial, services, submitLabel, onSubmit, onCancel, resetOnSuccess }) {
  const [form, setForm] = useState(initial);
  const [errors, setErrors] = useState({});
  const [saving, setSaving] = useState(false);
  const withService = Array.isArray(services);

  const update = (field) => (event) => setForm((current) => ({ ...current, [field]: event.target.value }));

  async function handleSubmit(event) {
    event.preventDefault();
    const validation = compact({
      name: validateRefName(form.name),
      description: validateRefDescription(form.description),
      serviceId: withService && !form.serviceId ? 'Le service est obligatoire' : null,
    });
    setErrors(validation);
    if (Object.keys(validation).length > 0) return;
    const data = { name: form.name.trim(), description: form.description.trim() || undefined };
    if (withService) data.serviceId = Number(form.serviceId);
    setSaving(true);
    const err = await onSubmit(data);
    setSaving(false);
    if (err) {
      const serverErrors = { ...(err.fieldErrors ?? {}) };
      if (err.status === 409 && /nom/i.test(err.message)) serverErrors.name = err.message;
      setErrors(serverErrors);
    } else if (resetOnSuccess) {
      setForm(initial);
      setErrors({});
    }
  }

  return (
    <form className="grid gap-3 md:grid-cols-4 md:items-start" onSubmit={handleSubmit} noValidate>
      <FormField id={`${idPrefix}-nom`} label="Nom" value={form.name} onChange={update('name')} error={errors.name} />
      <FormField
        id={`${idPrefix}-description`}
        label="Description"
        value={form.description}
        onChange={update('description')}
        error={errors.description}
        className={withService ? '' : 'md:col-span-2'}
      />
      {withService && (
        <div>
          <label htmlFor={`${idPrefix}-service`} className="mb-1 block text-sm font-medium text-slate-700">Service</label>
          <select
            id={`${idPrefix}-service`}
            className={`input ${errors.serviceId ? 'input-error' : ''}`}
            value={form.serviceId}
            onChange={update('serviceId')}
            aria-invalid={errors.serviceId ? 'true' : undefined}
          >
            <option value="">— Choisir —</option>
            {services.map((service) => (
              <option key={service.id} value={service.id}>{service.name}</option>
            ))}
          </select>
          {errors.serviceId && <p className="mt-1 text-sm text-red-600">{errors.serviceId}</p>}
        </div>
      )}
      <div className="flex gap-2 md:pt-6">
        <button type="submit" className="btn-primary" disabled={saving}>
          {saving && <Spinner className="h-4 w-4" />}
          {submitLabel}
        </button>
        {onCancel && (
          <button type="button" className="btn-secondary" onClick={onCancel} disabled={saving}>Annuler</button>
        )}
      </div>
    </form>
  );
}

// --- Agents des services -----------------------------------------------------------------------

function ServiceAgentsSection({ services, agents, onPerform }) {
  const [serviceId, setServiceId] = useState(services[0] ? String(services[0].id) : '');
  const [members, setMembers] = useState([]);
  const [membersLoading, setMembersLoading] = useState(false);
  const [membersError, setMembersError] = useState(null);
  const [agentId, setAgentId] = useState('');
  const [busy, setBusy] = useState(false);

  const loadMembers = useCallback(async () => {
    if (!serviceId) return;
    setMembersLoading(true);
    setMembersError(null);
    try {
      setMembers(await claimsApi.listServiceAgents(serviceId));
    } catch (err) {
      setMembersError(describeError(err));
    } finally {
      setMembersLoading(false);
    }
  }, [serviceId]);

  useEffect(() => {
    setAgentId('');
    loadMembers();
  }, [loadMembers]);

  const service = services.find((s) => String(s.id) === serviceId);
  const memberIds = new Set(members.map((m) => m.agentId));
  const candidates = agents.filter((agent) => agent.active && !memberIds.has(agent.id));

  async function add() {
    const agent = agents.find((a) => String(a.id) === agentId);
    if (!agent) return;
    setBusy(true);
    const err = await onPerform(
      () => claimsApi.addServiceAgent(Number(serviceId), agent.id, agent.name),
      `${agent.name} rattaché(e) au service « ${service?.name} ».`,
    );
    setBusy(false);
    if (!err) setAgentId('');
    await loadMembers();
  }

  async function remove(member) {
    if (!window.confirm(`Retirer ${member.agentName} du service « ${service?.name} » ?`)) return;
    setBusy(true);
    await onPerform(
      () => claimsApi.removeServiceAgent(Number(serviceId), member.agentId),
      `${member.agentName} retiré(e) du service « ${service?.name} ».`,
    );
    setBusy(false);
    await loadMembers();
  }

  return (
    <section className="card" aria-labelledby="agents-titre">
      <h2 id="agents-titre" className="text-lg font-medium text-slate-900">Agents des services</h2>
      <p className="mt-1 text-xs text-slate-500">
        Un agent voit les réclamations des services auxquels il est rattaché et peut y être affecté.
      </p>
      {services.length === 0 ? (
        <p className="mt-3 text-sm text-slate-500">Créez d’abord un service.</p>
      ) : (
        <div className="mt-4 space-y-4">
          <div className="sm:w-72">
            <label htmlFor="agents-service" className="mb-1 block text-sm font-medium text-slate-700">Service</label>
            <select id="agents-service" className="input" value={serviceId} onChange={(e) => setServiceId(e.target.value)}>
              {services.map((s) => (
                <option key={s.id} value={s.id}>{s.name}</option>
              ))}
            </select>
          </div>

          {membersLoading ? (
            <PageLoader label="Chargement des agents…" />
          ) : membersError ? (
            <Alert type="error">{membersError}</Alert>
          ) : members.length === 0 ? (
            <p className="text-sm text-slate-500">Aucun agent rattaché à ce service.</p>
          ) : (
            <ul className="divide-y divide-slate-100" aria-label={`Agents du service ${service?.name ?? ''}`}>
              {members.map((member) => (
                <li key={member.agentId} className="flex items-center justify-between gap-3 py-2 text-sm">
                  <span className="font-medium text-slate-800">
                    {member.agentName} <span className="font-normal text-slate-500">(n° {member.agentId})</span>
                  </span>
                  <button type="button" className="btn-danger py-1" disabled={busy} onClick={() => remove(member)}
                    aria-label={`Retirer ${member.agentName} du service`}>
                    Retirer
                  </button>
                </li>
              ))}
            </ul>
          )}

          <div className="flex flex-wrap items-end gap-3 border-t border-slate-200 pt-4">
            <div className="sm:w-72">
              <label htmlFor="agents-ajout" className="mb-1 block text-sm font-medium text-slate-700">Agent à rattacher</label>
              <select id="agents-ajout" className="input" value={agentId} onChange={(e) => setAgentId(e.target.value)}
                disabled={candidates.length === 0}>
                <option value="">{candidates.length === 0 ? 'Aucun agent disponible' : '— Choisir un agent —'}</option>
                {candidates.map((agent) => (
                  <option key={agent.id} value={agent.id}>{agent.name} ({agent.email})</option>
                ))}
              </select>
            </div>
            <button type="button" className="btn-primary" disabled={!agentId || busy} onClick={add}>
              {busy && <Spinner className="h-4 w-4" />}
              Rattacher
            </button>
          </div>
          {agents.length === 0 && (
            <p className="text-xs text-slate-500">
              Aucun compte AGENT : créez-en un dans la page Utilisateurs.
            </p>
          )}
        </div>
      )}
    </section>
  );
}

import { useCallback, useEffect, useState } from 'react';
import { authApi } from '../api/authApi';
import { useAuth } from '../auth/AuthContext';
import Alert from '../components/Alert';
import FormField from '../components/FormField';
import PageLoader, { Spinner } from '../components/PageLoader';
import { ROLE_LABELS, ROLES } from '../config';
import { formatDate } from '../utils/format';
import { compact, validateEmail, validateName, validatePassword } from '../utils/validation';

/** BF21 : gestion des utilisateurs (réservée au rôle ADMIN, vérifié aussi par la gateway). */
export default function AdminUsersPage() {
  const { user: currentUser } = useAuth();
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(null);
  const [feedback, setFeedback] = useState(null);
  const [busyId, setBusyId] = useState(null);

  const load = useCallback(async () => {
    setLoadError(null);
    try {
      setUsers(await authApi.listUsers());
    } catch (err) {
      setLoadError(err.message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const replaceUser = (updated) => setUsers((list) => list.map((u) => (u.id === updated.id ? updated : u)));

  async function changeRole(target, role) {
    if (role === target.role) return;
    setFeedback(null);
    setBusyId(target.id);
    try {
      const updated = await authApi.changeRole(target.id, role);
      replaceUser(updated);
      setFeedback({ type: 'success', text: `Rôle de ${updated.name} changé en « ${ROLE_LABELS[updated.role]} ».` });
    } catch (err) {
      setFeedback({ type: 'error', text: err.message });
    } finally {
      setBusyId(null);
    }
  }

  async function toggleActive(target) {
    const activate = !target.active;
    if (!activate && !window.confirm(`Désactiver le compte de ${target.name} ? Il ne pourra plus se connecter.`)) {
      return;
    }
    setFeedback(null);
    setBusyId(target.id);
    try {
      const updated = await authApi.changeStatus(target.id, activate);
      replaceUser(updated);
      setFeedback({
        type: 'success',
        text: `Compte de ${updated.name} ${updated.active ? 'activé' : 'désactivé'}.`,
      });
    } catch (err) {
      setFeedback({ type: 'error', text: err.message });
    } finally {
      setBusyId(null);
    }
  }

  function handleCreated(created) {
    setUsers((list) => [...list, created]);
    setFeedback({ type: 'success', text: `Compte ${ROLE_LABELS[created.role]} créé pour ${created.name}.` });
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Gestion des utilisateurs</h1>
        <p className="mt-1 text-sm text-slate-600">
          Créez des comptes agent ou administrateur, modifiez les rôles et activez ou désactivez les comptes.
        </p>
      </div>

      {feedback && (
        <Alert type={feedback.type} onClose={() => setFeedback(null)}>
          {feedback.text}
        </Alert>
      )}

      <CreateUserForm onCreated={handleCreated} />

      <section className="card p-0" aria-labelledby="liste-titre">
        <div className="flex items-center justify-between border-b border-slate-200 px-6 py-4">
          <h2 id="liste-titre" className="text-lg font-medium text-slate-900">
            Utilisateurs {!loading && !loadError && <span className="text-slate-500">({users.length})</span>}
          </h2>
          <button type="button" className="btn-secondary" onClick={load} disabled={loading}>
            Actualiser
          </button>
        </div>

        {loading ? (
          <PageLoader label="Chargement des utilisateurs…" />
        ) : loadError ? (
          <div className="p-6">
            <Alert type="error">{loadError}</Alert>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-slate-200 text-sm">
              <thead className="bg-slate-50 text-left text-xs font-medium uppercase tracking-wide text-slate-500">
                <tr>
                  <th scope="col" className="px-6 py-3">Nom</th>
                  <th scope="col" className="px-6 py-3">Email</th>
                  <th scope="col" className="px-6 py-3">Rôle</th>
                  <th scope="col" className="px-6 py-3">Statut</th>
                  <th scope="col" className="px-6 py-3">Créé le</th>
                  <th scope="col" className="px-6 py-3 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {users.map((u) => {
                  const isSelf = u.id === currentUser.id;
                  const busy = busyId === u.id;
                  return (
                    <tr key={u.id}>
                      <td className="whitespace-nowrap px-6 py-3 font-medium text-slate-900">
                        {u.name} {isSelf && <span className="text-xs font-normal text-slate-500">(vous)</span>}
                      </td>
                      <td className="whitespace-nowrap px-6 py-3 text-slate-700">{u.email}</td>
                      <td className="px-6 py-3">
                        <label className="sr-only" htmlFor={`role-${u.id}`}>Rôle de {u.name}</label>
                        <select
                          id={`role-${u.id}`}
                          className="input py-1"
                          value={u.role}
                          disabled={isSelf || busy}
                          onChange={(e) => changeRole(u, e.target.value)}
                        >
                          {ROLES.map((role) => (
                            <option key={role} value={role}>{ROLE_LABELS[role]}</option>
                          ))}
                        </select>
                      </td>
                      <td className="whitespace-nowrap px-6 py-3">
                        <span
                          className={`rounded-full px-2 py-0.5 text-xs font-medium ${
                            u.active ? 'bg-emerald-100 text-emerald-800' : 'bg-slate-200 text-slate-700'
                          }`}
                        >
                          {u.active ? 'Actif' : 'Désactivé'}
                        </span>
                      </td>
                      <td className="whitespace-nowrap px-6 py-3 text-slate-600">{formatDate(u.createdAt)}</td>
                      <td className="whitespace-nowrap px-6 py-3 text-right">
                        {isSelf ? (
                          <span className="text-xs text-slate-400">—</span>
                        ) : (
                          <button
                            type="button"
                            className={u.active ? 'btn-danger py-1' : 'btn-success py-1'}
                            disabled={busy}
                            onClick={() => toggleActive(u)}
                            aria-label={`${u.active ? 'Désactiver' : 'Activer'} le compte de ${u.name}`}
                          >
                            {busy && <Spinner className="h-3 w-3" />}
                            {u.active ? 'Désactiver' : 'Activer'}
                          </button>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
}

function CreateUserForm({ onCreated }) {
  const empty = { name: '', email: '', password: '', role: 'AGENT' };
  const [form, setForm] = useState(empty);
  const [fieldErrors, setFieldErrors] = useState({});
  const [error, setError] = useState(null);
  const [saving, setSaving] = useState(false);

  const update = (field) => (event) => setForm((current) => ({ ...current, [field]: event.target.value }));

  async function handleSubmit(event) {
    event.preventDefault();
    setError(null);
    const errors = compact({
      name: validateName(form.name),
      email: validateEmail(form.email),
      password: validatePassword(form.password),
    });
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) return;

    setSaving(true);
    try {
      const created = await authApi.createUser({
        name: form.name.trim(),
        email: form.email.trim(),
        password: form.password,
        role: form.role,
      });
      setForm(empty);
      onCreated(created);
    } catch (err) {
      const serverErrors = { ...(err.fieldErrors ?? {}) };
      if (err.status === 409) serverErrors.email = err.message;
      setFieldErrors(serverErrors);
      setError(err.status === 409 ? null : err.message);
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="card" aria-labelledby="creation-titre">
      <h2 id="creation-titre" className="text-lg font-medium text-slate-900">Créer un agent ou un administrateur</h2>
      <p className="mt-1 text-sm text-slate-600">Les clients créent eux-mêmes leur compte via l'inscription.</p>
      {error && (
        <div className="mt-4">
          <Alert type="error">{error}</Alert>
        </div>
      )}
      <form className="mt-4 grid gap-4 md:grid-cols-2 lg:grid-cols-5 lg:items-start" onSubmit={handleSubmit} noValidate>
        <FormField id="new-name" label="Nom complet" value={form.name} onChange={update('name')} error={fieldErrors.name} />
        <FormField id="new-email" label="Email" type="email" value={form.email} onChange={update('email')}
          error={fieldErrors.email} />
        <FormField id="new-password" label="Mot de passe initial" type="password" autoComplete="new-password"
          value={form.password} onChange={update('password')} error={fieldErrors.password} hint="Au moins 8 caractères." />
        <div>
          <label htmlFor="new-role" className="mb-1 block text-sm font-medium text-slate-700">Rôle</label>
          <select id="new-role" className="input" value={form.role} onChange={update('role')}>
            <option value="AGENT">{ROLE_LABELS.AGENT}</option>
            <option value="ADMIN">{ROLE_LABELS.ADMIN}</option>
          </select>
        </div>
        <div className="lg:pt-6">
          <button type="submit" className="btn-primary w-full" disabled={saving}>
            {saving && <Spinner className="h-4 w-4" />}
            Créer le compte
          </button>
        </div>
      </form>
    </section>
  );
}

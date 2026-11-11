import { useState } from 'react';
import { authApi } from '../api/authApi';
import { useAuth } from '../auth/AuthContext';
import Alert from '../components/Alert';
import FormField from '../components/FormField';
import { Spinner } from '../components/PageLoader';
import { ROLE_LABELS } from '../config';
import { formatDate } from '../utils/format';
import { compact, validateName, validatePassword } from '../utils/validation';

/** BF4 : consultation et modification du profil. */
export default function ProfilePage() {
  const { user } = useAuth();

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Mon profil</h1>
        <p className="mt-1 text-sm text-slate-600">Consultez et modifiez vos informations personnelles.</p>
      </div>

      <section className="card" aria-labelledby="infos-titre">
        <h2 id="infos-titre" className="text-lg font-medium text-slate-900">Informations du compte</h2>
        <dl className="mt-4 grid gap-4 sm:grid-cols-2">
          <Info label="Nom" value={user.name} />
          <Info label="Email" value={user.email} />
          <Info label="Rôle" value={ROLE_LABELS[user.role] ?? user.role} />
          <Info label="Membre depuis" value={formatDate(user.createdAt)} />
        </dl>
      </section>

      <div className="grid gap-6 lg:grid-cols-2">
        <NameForm />
        <PasswordForm />
      </div>
    </div>
  );
}

function Info({ label, value }) {
  return (
    <div>
      <dt className="text-xs font-medium uppercase tracking-wide text-slate-500">{label}</dt>
      <dd className="mt-1 text-sm text-slate-900">{value}</dd>
    </div>
  );
}

function NameForm() {
  const { user, updateUser } = useAuth();
  const [name, setName] = useState(user.name);
  const [fieldErrors, setFieldErrors] = useState({});
  const [message, setMessage] = useState(null);
  const [saving, setSaving] = useState(false);

  async function handleSubmit(event) {
    event.preventDefault();
    setMessage(null);
    const errors = compact({ name: validateName(name) });
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) return;

    setSaving(true);
    try {
      const updated = await authApi.updateMe({ name: name.trim() });
      updateUser(updated);
      setMessage({ type: 'success', text: 'Votre nom a été mis à jour.' });
    } catch (err) {
      setFieldErrors(err.fieldErrors ?? {});
      setMessage({ type: 'error', text: err.message });
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="card" aria-labelledby="nom-titre">
      <h2 id="nom-titre" className="text-lg font-medium text-slate-900">Modifier mon nom</h2>
      <form className="mt-4 space-y-4" onSubmit={handleSubmit} noValidate>
        {message && <Alert type={message.type}>{message.text}</Alert>}
        <FormField id="profil-nom" label="Nom complet" autoComplete="name" value={name}
          onChange={(e) => setName(e.target.value)} error={fieldErrors.name} />
        <button type="submit" className="btn-primary" disabled={saving || name.trim() === user.name}>
          {saving && <Spinner className="h-4 w-4" />}
          Enregistrer
        </button>
      </form>
    </section>
  );
}

function PasswordForm() {
  const { updateUser } = useAuth();
  const empty = { currentPassword: '', newPassword: '', confirm: '' };
  const [form, setForm] = useState(empty);
  const [fieldErrors, setFieldErrors] = useState({});
  const [message, setMessage] = useState(null);
  const [saving, setSaving] = useState(false);

  const update = (field) => (event) => setForm((current) => ({ ...current, [field]: event.target.value }));

  async function handleSubmit(event) {
    event.preventDefault();
    setMessage(null);
    const errors = compact({
      currentPassword: form.currentPassword ? null : 'Saisissez votre mot de passe actuel',
      newPassword: validatePassword(form.newPassword),
      confirm: form.confirm === form.newPassword ? null : 'Les mots de passe ne correspondent pas',
    });
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) return;

    setSaving(true);
    try {
      const updated = await authApi.updateMe({
        currentPassword: form.currentPassword,
        newPassword: form.newPassword,
      });
      updateUser(updated);
      setForm(empty);
      setMessage({ type: 'success', text: 'Votre mot de passe a été modifié.' });
    } catch (err) {
      setFieldErrors(err.fieldErrors ?? {});
      setMessage({ type: 'error', text: err.message });
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="card" aria-labelledby="mdp-titre">
      <h2 id="mdp-titre" className="text-lg font-medium text-slate-900">Changer mon mot de passe</h2>
      <form className="mt-4 space-y-4" onSubmit={handleSubmit} noValidate>
        {message && <Alert type={message.type}>{message.text}</Alert>}
        <FormField id="currentPassword" label="Mot de passe actuel" type="password" autoComplete="current-password"
          value={form.currentPassword} onChange={update('currentPassword')} error={fieldErrors.currentPassword} />
        <FormField id="newPassword" label="Nouveau mot de passe" type="password" autoComplete="new-password"
          value={form.newPassword} onChange={update('newPassword')} error={fieldErrors.newPassword}
          hint="Au moins 8 caractères." />
        <FormField id="confirmNewPassword" label="Confirmer le nouveau mot de passe" type="password"
          autoComplete="new-password" value={form.confirm} onChange={update('confirm')} error={fieldErrors.confirm} />
        <button type="submit" className="btn-primary" disabled={saving}>
          {saving && <Spinner className="h-4 w-4" />}
          Changer le mot de passe
        </button>
      </form>
    </section>
  );
}

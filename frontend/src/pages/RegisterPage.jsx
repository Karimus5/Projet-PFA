import { useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { useAuth } from '../auth/AuthContext';
import Alert from '../components/Alert';
import FormField from '../components/FormField';
import { Spinner } from '../components/PageLoader';
import { compact, validateEmail, validateName, validatePassword } from '../utils/validation';

/** BF1 : inscription d'un client (le rôle CLIENT est imposé par le serveur). */
export default function RegisterPage() {
  const { register } = useAuth();
  const navigate = useNavigate();

  const [form, setForm] = useState({ name: '', email: '', password: '', confirm: '' });
  const [fieldErrors, setFieldErrors] = useState({});
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  const update = (field) => (event) => setForm((current) => ({ ...current, [field]: event.target.value }));

  async function handleSubmit(event) {
    event.preventDefault();
    setError(null);

    const errors = compact({
      name: validateName(form.name),
      email: validateEmail(form.email),
      password: validatePassword(form.password),
      confirm: form.confirm === form.password ? null : 'Les mots de passe ne correspondent pas',
    });
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) return;

    setSubmitting(true);
    try {
      await register({ name: form.name.trim(), email: form.email.trim(), password: form.password });
      navigate('/profil', { replace: true });
    } catch (err) {
      const serverFieldErrors = { ...(err.fieldErrors ?? {}) };
      if (err.status === 409) serverFieldErrors.email = err.message; // email déjà utilisé
      setFieldErrors(serverFieldErrors);
      setError(err.status === 409 ? null : err.message);
      setSubmitting(false);
    }
  }

  return (
    <div className="mx-auto max-w-md">
      <div className="card">
        <h1 className="text-2xl font-semibold text-slate-900">Créer un compte</h1>
        <p className="mt-1 text-sm text-slate-600">Inscrivez-vous pour soumettre et suivre vos réclamations.</p>

        {error && (
          <div className="mt-4">
            <Alert type="error">{error}</Alert>
          </div>
        )}

        <form className="mt-4 space-y-4" onSubmit={handleSubmit} noValidate>
          <FormField id="name" label="Nom complet" autoComplete="name" value={form.name}
            onChange={update('name')} error={fieldErrors.name} />
          <FormField id="email" label="Email" type="email" autoComplete="email" value={form.email}
            onChange={update('email')} error={fieldErrors.email} />
          <FormField id="password" label="Mot de passe" type="password" autoComplete="new-password"
            value={form.password} onChange={update('password')} error={fieldErrors.password}
            hint="Au moins 8 caractères." />
          <FormField id="confirm" label="Confirmer le mot de passe" type="password" autoComplete="new-password"
            value={form.confirm} onChange={update('confirm')} error={fieldErrors.confirm} />
          <button type="submit" className="btn-primary w-full" disabled={submitting}>
            {submitting && <Spinner className="h-4 w-4" />}
            {submitting ? 'Création du compte…' : "S'inscrire"}
          </button>
        </form>

        <p className="mt-6 text-center text-sm text-slate-600">
          Déjà inscrit ?{' '}
          <Link to="/connexion" className="font-medium text-indigo-600 hover:underline">
            Se connecter
          </Link>
        </p>
      </div>
    </div>
  );
}

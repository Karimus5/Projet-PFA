import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router';
import { useAuth } from '../auth/AuthContext';
import Alert from '../components/Alert';
import FormField from '../components/FormField';
import { Spinner } from '../components/PageLoader';
import { compact, validateEmail } from '../utils/validation';

export default function LoginPage() {
  const { login, notice, clearNotice } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const [form, setForm] = useState({ email: '', password: '' });
  const [fieldErrors, setFieldErrors] = useState({});
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  const update = (field) => (event) => setForm((current) => ({ ...current, [field]: event.target.value }));

  async function handleSubmit(event) {
    event.preventDefault();
    clearNotice();
    setError(null);

    const errors = compact({
      email: validateEmail(form.email),
      password: form.password ? null : 'Le mot de passe est obligatoire',
    });
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) return;

    setSubmitting(true);
    try {
      await login(form.email.trim(), form.password);
      navigate(location.state?.from || '/profil', { replace: true });
    } catch (err) {
      setFieldErrors(err.fieldErrors ?? {});
      setError(err.message);
      setSubmitting(false);
    }
  }

  return (
    <div className="mx-auto max-w-md">
      <div className="card">
        <h1 className="text-2xl font-semibold text-slate-900">Connexion</h1>
        <p className="mt-1 text-sm text-slate-600">Accédez à votre espace de suivi des réclamations.</p>

        <div className="mt-4 space-y-3">
          {notice && <Alert type="info">{notice}</Alert>}
          {error && <Alert type="error">{error}</Alert>}
        </div>

        <form className="mt-4 space-y-4" onSubmit={handleSubmit} noValidate>
          <FormField
            id="email"
            label="Email"
            type="email"
            autoComplete="email"
            value={form.email}
            onChange={update('email')}
            error={fieldErrors.email}
          />
          <FormField
            id="password"
            label="Mot de passe"
            type="password"
            autoComplete="current-password"
            value={form.password}
            onChange={update('password')}
            error={fieldErrors.password}
          />
          <button type="submit" className="btn-primary w-full" disabled={submitting}>
            {submitting && <Spinner className="h-4 w-4" />}
            {submitting ? 'Connexion…' : 'Se connecter'}
          </button>
        </form>

        <p className="mt-6 text-center text-sm text-slate-600">
          Pas encore de compte ?{' '}
          <Link to="/inscription" className="font-medium text-indigo-600 hover:underline">
            Créer un compte client
          </Link>
        </p>
      </div>
    </div>
  );
}

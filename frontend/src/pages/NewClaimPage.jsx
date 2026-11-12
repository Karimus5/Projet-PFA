import { useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { claimsApi } from '../api/claimsApi';
import { validateFiles } from '../claims/attachmentRules';
import { describeError } from '../claims/errors';
import Alert from '../components/Alert';
import FormField from '../components/FormField';
import { Spinner } from '../components/PageLoader';
import TextAreaField from '../components/TextAreaField';
import FilePicker from '../components/claims/FilePicker';
import { compact, validateClaimDescription, validateClaimTitle } from '../utils/validation';

/**
 * BF6 : soumission d'une réclamation (titre, description, pièces jointes facultatives).
 * Le client ne choisit ni catégorie ni service : le classement est fait ensuite (admin, puis IA).
 * Deux appels : création, puis envoi des fichiers ; si l'envoi échoue, la réclamation existe
 * quand même et le client est prévenu sur la page de détail (il peut réessayer l'ajout).
 */
export default function NewClaimPage() {
  const navigate = useNavigate();
  const [form, setForm] = useState({ title: '', description: '' });
  const [files, setFiles] = useState([]);
  const [fileErrors, setFileErrors] = useState([]);
  const [fieldErrors, setFieldErrors] = useState({});
  const [error, setError] = useState(null);
  const [saving, setSaving] = useState(false);

  const update = (field) => (event) => setForm((current) => ({ ...current, [field]: event.target.value }));

  function handleFiles(next) {
    setFiles(next);
    setFileErrors(validateFiles(next));
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setError(null);
    const errors = compact({
      title: validateClaimTitle(form.title),
      description: validateClaimDescription(form.description),
    });
    const filesValidation = validateFiles(files);
    setFieldErrors(errors);
    setFileErrors(filesValidation);
    if (Object.keys(errors).length > 0 || filesValidation.length > 0) return;

    setSaving(true);
    let created;
    try {
      created = await claimsApi.create({ title: form.title.trim(), description: form.description.trim() });
    } catch (err) {
      setFieldErrors(err.fieldErrors ?? {});
      setError(describeError(err));
      setSaving(false);
      return;
    }

    let notice = { type: 'success', text: `Réclamation n° ${created.id} enregistrée.` };
    if (files.length > 0) {
      try {
        await claimsApi.uploadAttachments(created.id, files);
        notice = { type: 'success', text: `Réclamation n° ${created.id} enregistrée avec ${files.length} pièce(s) jointe(s).` };
      } catch (err) {
        notice = {
          type: 'error',
          text: `Réclamation n° ${created.id} enregistrée, mais l’envoi des pièces jointes a échoué (${describeError(err)}). Vous pouvez les ajouter ci-dessous.`,
        };
      }
    }
    navigate(`/reclamations/${created.id}`, { state: { notice } });
  }

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <div>
        <Link to="/reclamations" className="text-sm text-indigo-700 hover:underline">← Mes réclamations</Link>
        <h1 className="mt-2 text-2xl font-semibold text-slate-900">Nouvelle réclamation</h1>
        <p className="mt-1 text-sm text-slate-600">
          Décrivez votre problème : votre réclamation sera classée puis transmise au service concerné.
        </p>
      </div>

      {error && <Alert type="error">{error}</Alert>}

      <form className="card space-y-5" onSubmit={handleSubmit} noValidate>
        <FormField
          id="titre"
          label="Titre"
          value={form.title}
          onChange={update('title')}
          error={fieldErrors.title}
          maxLength={150}
          hint="150 caractères maximum."
        />
        <TextAreaField
          id="description"
          label="Description"
          rows={8}
          maxLength={5000}
          value={form.description}
          onChange={update('description')}
          error={fieldErrors.description}
        />
        <FilePicker
          id="pieces-jointes"
          label="Pièces jointes (facultatives)"
          files={files}
          onChange={handleFiles}
          errors={fileErrors}
          disabled={saving}
        />
        <div className="flex justify-end gap-2">
          <Link to="/reclamations" className="btn-secondary">Annuler</Link>
          <button type="submit" className="btn-primary" disabled={saving || fileErrors.length > 0}>
            {saving && <Spinner className="h-4 w-4" />}
            Soumettre la réclamation
          </button>
        </div>
      </form>
    </div>
  );
}

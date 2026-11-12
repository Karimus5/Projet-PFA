import { useState } from 'react';
import { claimsApi } from '../../api/claimsApi';
import { validateComplement } from '../../utils/validation';
import { Spinner } from '../PageLoader';
import TextAreaField from '../TextAreaField';

/** BF9 : complément d'information du client, ajouté à la fin de la description. */
export default function ComplementForm({ claimId, onPerform }) {
  const [open, setOpen] = useState(false);
  const [text, setText] = useState('');
  const [error, setError] = useState(null);
  const [saving, setSaving] = useState(false);

  if (!open) {
    return (
      <button type="button" className="btn-secondary mt-4" onClick={() => setOpen(true)}>
        Ajouter un complément d'information
      </button>
    );
  }

  async function handleSubmit(event) {
    event.preventDefault();
    const validation = validateComplement(text);
    setError(validation);
    if (validation) return;
    setSaving(true);
    const err = await onPerform(() => claimsApi.addComplement(claimId, text.trim()), 'Complément ajouté à la description.');
    setSaving(false);
    if (err) {
      setError(err.fieldErrors?.text ?? null);
    } else {
      setText('');
      setOpen(false);
    }
  }

  return (
    <form className="mt-4 space-y-3 border-t border-slate-200 pt-4" onSubmit={handleSubmit} noValidate>
      <TextAreaField
        id="complement"
        label="Complément d'information"
        rows={3}
        maxLength={2000}
        value={text}
        onChange={(e) => setText(e.target.value)}
        error={error}
      />
      <div className="flex gap-2">
        <button type="submit" className="btn-primary" disabled={saving}>
          {saving && <Spinner className="h-4 w-4" />}
          Ajouter le complément
        </button>
        <button type="button" className="btn-secondary" onClick={() => setOpen(false)} disabled={saving}>
          Annuler
        </button>
      </div>
    </form>
  );
}

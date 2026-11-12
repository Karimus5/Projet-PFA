import { useState } from 'react';
import { claimsApi } from '../../api/claimsApi';
import { STATUS_LABELS, statusActionsFor } from '../../claims/claimRules';
import { validateNote } from '../../utils/validation';
import { Spinner } from '../PageLoader';
import TextAreaField from '../TextAreaField';

/**
 * Changement de statut (BF12) : seules les transitions autorisées au rôle et au statut actuel
 * sont proposées (voir claimRules.js). Note facultative, enregistrée dans l'historique.
 */
export default function StatusActions({ claim, user, onPerform }) {
  const actions = statusActionsFor(claim, user);
  const [note, setNote] = useState('');
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(null);

  if (actions.length === 0) {
    const hint = hintFor(claim, user);
    return hint ? (
      <section className="card" aria-labelledby="statut-titre">
        <h2 id="statut-titre" className="text-lg font-medium text-slate-900">Statut</h2>
        <p className="mt-2 text-sm text-slate-600">{hint}</p>
      </section>
    ) : null;
  }

  async function run(action) {
    const validation = validateNote(note);
    setError(validation);
    if (validation) return;
    setBusy(action.to);
    const err = await onPerform(
      () => claimsApi.changeStatus(claim.id, action.to, note.trim()),
      `Statut passé à « ${STATUS_LABELS[action.to]} ».`,
    );
    setBusy(null);
    if (err) setError(err.fieldErrors?.note ?? null);
    else setNote('');
  }

  return (
    <section className="card space-y-3" aria-labelledby="statut-titre">
      <h2 id="statut-titre" className="text-lg font-medium text-slate-900">Faire avancer la réclamation</h2>
      <TextAreaField
        id="note-statut"
        label="Note (facultative, visible dans l'historique)"
        rows={2}
        maxLength={500}
        value={note}
        onChange={(e) => setNote(e.target.value)}
        error={error}
      />
      <div className="flex flex-wrap gap-2">
        {actions.map((action) => (
          <button
            key={action.to}
            type="button"
            className={action.style}
            disabled={busy !== null}
            onClick={() => run(action)}
          >
            {busy === action.to && <Spinner className="h-4 w-4" />}
            {action.label}
          </button>
        ))}
      </div>
    </section>
  );
}

function hintFor(claim, user) {
  if (user.role === 'CLIENT') {
    if (claim.status === 'CLOSED') return 'Réclamation clôturée. Merci pour votre retour.';
    if (claim.status === 'REJECTED') return 'Réclamation rejetée.';
    return 'Votre réclamation est en cours de traitement. Vous pourrez la clôturer ou la rouvrir une fois résolue.';
  }
  if (claim.status === 'NEW') {
    return user.role === 'ADMIN'
      ? null
      : "Réclamation en attente de classement par un administrateur.";
  }
  if (claim.status === 'CLASSIFIED') return 'Affectez la réclamation à un agent pour pouvoir la prendre en charge.';
  if (claim.status === 'RESOLVED') return 'Réclamation résolue : en attente de la validation du client (clôture ou réouverture).';
  return null;
}

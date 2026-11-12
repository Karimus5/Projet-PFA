import { useState } from 'react';
import { claimsApi } from '../../api/claimsApi';
import { canSeeInternalComments, isOpen, visibleComments } from '../../claims/claimRules';
import { formatDateTime } from '../../utils/format';
import { validateComment } from '../../utils/validation';
import { Spinner } from '../PageLoader';
import TextAreaField from '../TextAreaField';
import { authorLabel } from './HistoryTimeline';

/**
 * Commentaires (BF13). Les commentaires internes (agents et admins) sont mis en évidence ; un client
 * ne les reçoit pas du serveur et, par sécurité, ils sont aussi filtrés ici.
 */
export default function CommentsSection({ claim, user, onPerform }) {
  const comments = visibleComments(claim.comments, user);
  const staff = canSeeInternalComments(user);

  return (
    <section className="card" aria-labelledby="commentaires-titre">
      <h2 id="commentaires-titre" className="text-lg font-medium text-slate-900">
        Commentaires <span className="text-slate-500">({comments.length})</span>
      </h2>
      {staff && (
        <p className="mt-1 text-xs text-slate-500">
          Les commentaires <strong>internes</strong> (fond ambré) ne sont jamais visibles par le client.
        </p>
      )}

      {comments.length === 0 ? (
        <p className="mt-4 text-sm text-slate-500">Aucun commentaire pour le moment.</p>
      ) : (
        <ul className="mt-4 space-y-3" aria-label="Liste des commentaires">
          {comments.map((comment) => (
            <li
              key={comment.id}
              data-testid={comment.internal ? 'commentaire-interne' : 'commentaire-public'}
              className={`rounded-md border px-4 py-3 ${
                comment.internal ? 'border-amber-300 bg-amber-50' : 'border-slate-200 bg-white'
              }`}
            >
              <div className="flex flex-wrap items-center gap-2 text-xs text-slate-500">
                <span className="font-medium text-slate-700">{authorLabel(comment, user)}</span>
                <span>· {formatDateTime(comment.createdAt)}</span>
                {comment.internal ? (
                  <span className="rounded bg-amber-200 px-1.5 py-0.5 font-medium text-amber-900">Interne</span>
                ) : (
                  staff && <span className="rounded bg-sky-100 px-1.5 py-0.5 font-medium text-sky-800">Public</span>
                )}
              </div>
              <p className="mt-1 whitespace-pre-line text-sm text-slate-800">{comment.content}</p>
            </li>
          ))}
        </ul>
      )}

      {isOpen(claim.status) ? (
        <CommentForm claimId={claim.id} staff={staff} onPerform={onPerform} />
      ) : (
        <p className="mt-4 text-sm text-slate-500">Réclamation terminée : il n'est plus possible de commenter.</p>
      )}
    </section>
  );
}

function CommentForm({ claimId, staff, onPerform }) {
  const [content, setContent] = useState('');
  const [internal, setInternal] = useState(false);
  const [error, setError] = useState(null);
  const [saving, setSaving] = useState(false);

  async function handleSubmit(event) {
    event.preventDefault();
    const validation = validateComment(content);
    setError(validation);
    if (validation) return;
    setSaving(true);
    const err = await onPerform(
      () => claimsApi.addComment(claimId, content.trim(), staff && internal),
      staff && internal ? 'Commentaire interne ajouté.' : 'Commentaire ajouté.',
    );
    setSaving(false);
    if (err) {
      setError(err.fieldErrors?.content ?? null);
    } else {
      setContent('');
      setInternal(false);
    }
  }

  return (
    <form className="mt-5 space-y-3 border-t border-slate-200 pt-4" onSubmit={handleSubmit} noValidate>
      <TextAreaField
        id="nouveau-commentaire"
        label={staff ? 'Nouveau commentaire' : 'Votre message'}
        rows={3}
        maxLength={2000}
        value={content}
        onChange={(e) => setContent(e.target.value)}
        error={error}
      />
      <div className="flex flex-wrap items-center justify-between gap-3">
        {staff ? (
          <label className="flex items-center gap-2 text-sm text-slate-700">
            <input type="checkbox" checked={internal} onChange={(e) => setInternal(e.target.checked)} />
            Commentaire interne (invisible pour le client)
          </label>
        ) : (
          <span />
        )}
        <button type="submit" className="btn-primary" disabled={saving}>
          {saving && <Spinner className="h-4 w-4" />}
          Envoyer le commentaire
        </button>
      </div>
    </form>
  );
}

import { useState } from 'react';
import { claimsApi } from '../../api/claimsApi';
import { MAX_FILES_PER_CLAIM, saveBlob, validateFiles } from '../../claims/attachmentRules';
import { isOpen } from '../../claims/claimRules';
import { describeError } from '../../claims/errors';
import { formatDateTime, formatSize } from '../../utils/format';
import Alert from '../Alert';
import { Spinner } from '../PageLoader';
import FilePicker from './FilePicker';

/** Pièces jointes : liste, téléchargement authentifié, ajout tant que la réclamation est ouverte. */
export default function AttachmentsSection({ claim, onPerform }) {
  const attachments = claim.attachments ?? [];
  const [downloadingId, setDownloadingId] = useState(null);
  const [downloadError, setDownloadError] = useState(null);
  const canUpload = isOpen(claim.status) && attachments.length < MAX_FILES_PER_CLAIM;

  async function download(attachment) {
    setDownloadError(null);
    setDownloadingId(attachment.id);
    try {
      const blob = await claimsApi.downloadAttachment(claim.id, attachment.id);
      saveBlob(blob, attachment.fileName);
    } catch (err) {
      setDownloadError(describeError(err));
    } finally {
      setDownloadingId(null);
    }
  }

  return (
    <section className="card" aria-labelledby="pieces-titre">
      <h2 id="pieces-titre" className="text-lg font-medium text-slate-900">
        Pièces jointes <span className="text-slate-500">({attachments.length} / {MAX_FILES_PER_CLAIM})</span>
      </h2>
      {downloadError && (
        <div className="mt-3">
          <Alert type="error" onClose={() => setDownloadError(null)}>{downloadError}</Alert>
        </div>
      )}

      {attachments.length === 0 ? (
        <p className="mt-3 text-sm text-slate-500">Aucune pièce jointe.</p>
      ) : (
        <ul className="mt-3 divide-y divide-slate-100" aria-label="Liste des pièces jointes">
          {attachments.map((attachment) => (
            <li key={attachment.id} className="flex items-center justify-between gap-3 py-2">
              <div className="min-w-0 text-sm">
                <p className="truncate font-medium text-slate-800">{attachment.fileName}</p>
                <p className="text-xs text-slate-500">
                  {formatSize(attachment.size)} · {formatDateTime(attachment.uploadedAt)}
                </p>
              </div>
              <button
                type="button"
                className="btn-secondary py-1"
                disabled={downloadingId === attachment.id}
                onClick={() => download(attachment)}
                aria-label={`Télécharger ${attachment.fileName}`}
              >
                {downloadingId === attachment.id && <Spinner className="h-3 w-3" />}
                Télécharger
              </button>
            </li>
          ))}
        </ul>
      )}

      {canUpload ? (
        <UploadForm claim={claim} onPerform={onPerform} />
      ) : (
        <p className="mt-3 text-xs text-slate-500">
          {isOpen(claim.status)
            ? `Nombre maximal de pièces jointes atteint (${MAX_FILES_PER_CLAIM}).`
            : 'Réclamation terminée : ajout de pièces jointes impossible.'}
        </p>
      )}
    </section>
  );
}

function UploadForm({ claim, onPerform }) {
  const [files, setFiles] = useState([]);
  const [errors, setErrors] = useState([]);
  const [sending, setSending] = useState(false);
  const already = claim.attachments?.length ?? 0;

  function handleChange(next) {
    setFiles(next);
    setErrors(validateFiles(next, already));
  }

  async function handleSubmit(event) {
    event.preventDefault();
    const validation = validateFiles(files, already);
    setErrors(validation);
    if (validation.length > 0 || files.length === 0) return;
    setSending(true);
    const err = await onPerform(
      () => claimsApi.uploadAttachments(claim.id, files),
      files.length > 1 ? `${files.length} pièces jointes ajoutées.` : 'Pièce jointe ajoutée.',
    );
    setSending(false);
    if (!err) setFiles([]);
  }

  return (
    <form className="mt-4 space-y-3 border-t border-slate-200 pt-4" onSubmit={handleSubmit} noValidate>
      <FilePicker
        id="ajout-pieces"
        label="Ajouter des pièces jointes"
        files={files}
        onChange={handleChange}
        errors={errors}
        alreadyAttached={already}
        disabled={sending}
      />
      <button type="submit" className="btn-primary" disabled={sending || files.length === 0 || errors.length > 0}>
        {sending && <Spinner className="h-4 w-4" />}
        Envoyer les fichiers
      </button>
    </form>
  );
}

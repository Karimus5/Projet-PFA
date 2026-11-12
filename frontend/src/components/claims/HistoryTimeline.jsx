import { ROLE_LABELS } from '../../config';
import { formatDateTime } from '../../utils/format';
import StatusBadge from './StatusBadge';

/** Historique des statuts (BF8), du plus ancien au plus récent. */
export default function HistoryTimeline({ history, currentUser }) {
  if (!history?.length) return <p className="text-sm text-slate-500">Aucun historique.</p>;
  return (
    <ol className="space-y-4" aria-label="Historique des statuts">
      {history.map((entry, index) => (
        <li key={`${entry.changedAt}-${index}`} className="relative border-l-2 border-slate-200 pl-4">
          <span aria-hidden="true" className="absolute -left-[5px] top-1.5 h-2 w-2 rounded-full bg-indigo-500" />
          <div className="flex flex-wrap items-center gap-1.5 text-sm">
            {entry.oldStatus ? (
              <>
                <StatusBadge status={entry.oldStatus} />
                <span aria-label="vers">→</span>
              </>
            ) : null}
            <StatusBadge status={entry.newStatus} />
          </div>
          <p className="mt-1 text-xs text-slate-500">
            {formatDateTime(entry.changedAt)} · {authorLabel(entry, currentUser)}
          </p>
          {entry.note && <p className="mt-1 text-sm text-slate-700">{entry.note}</p>}
        </li>
      ))}
    </ol>
  );
}

export function authorLabel(entry, currentUser) {
  if (currentUser && entry.authorId === currentUser.id && entry.authorRole === currentUser.role) return 'Vous';
  return ROLE_LABELS[entry.authorRole] ?? entry.authorRole;
}

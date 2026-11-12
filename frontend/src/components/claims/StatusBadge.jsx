import { STATUS_LABELS, STATUS_STYLES } from '../../claims/claimRules';

export default function StatusBadge({ status }) {
  return (
    <span
      className={`inline-flex whitespace-nowrap rounded-full px-2.5 py-0.5 text-xs font-medium ${
        STATUS_STYLES[status] ?? 'bg-slate-100 text-slate-700'
      }`}
    >
      {STATUS_LABELS[status] ?? status}
    </span>
  );
}

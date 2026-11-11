const STYLES = {
  error: 'border-red-200 bg-red-50 text-red-800',
  success: 'border-emerald-200 bg-emerald-50 text-emerald-800',
  info: 'border-sky-200 bg-sky-50 text-sky-800',
};

/** Message de retour. Les erreurs sont annoncées immédiatement aux lecteurs d'écran (role="alert"). */
export default function Alert({ type = 'info', children, onClose }) {
  if (!children) return null;
  return (
    <div
      role={type === 'error' ? 'alert' : 'status'}
      className={`flex items-start justify-between gap-3 rounded-md border px-4 py-3 text-sm ${STYLES[type]}`}
    >
      <div>{children}</div>
      {onClose && (
        <button type="button" onClick={onClose} className="text-current opacity-70 hover:opacity-100" aria-label="Fermer le message">
          ×
        </button>
      )}
    </div>
  );
}

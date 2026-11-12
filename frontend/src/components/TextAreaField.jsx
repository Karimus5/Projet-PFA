/** Zone de texte avec libellé, compteur facultatif et message d'erreur associé (accessible). */
export default function TextAreaField({ id, label, error, hint, maxLength, value = '', className = '', rows = 4, ...props }) {
  const messageId = error ? `${id}-erreur` : hint ? `${id}-aide` : undefined;
  return (
    <div className={className}>
      <div className="mb-1 flex items-baseline justify-between gap-2">
        <label htmlFor={id} className="block text-sm font-medium text-slate-700">
          {label}
        </label>
        {maxLength && (
          <span className={`text-xs ${value.length > maxLength ? 'text-red-600' : 'text-slate-400'}`}>
            {value.length} / {maxLength}
          </span>
        )}
      </div>
      <textarea
        id={id}
        name={id}
        rows={rows}
        value={value}
        className={`input ${error ? 'input-error' : ''}`}
        aria-invalid={error ? 'true' : undefined}
        aria-describedby={messageId}
        {...props}
      />
      {error ? (
        <p id={messageId} className="mt-1 text-sm text-red-600">
          {error}
        </p>
      ) : hint ? (
        <p id={messageId} className="mt-1 text-xs text-slate-500">
          {hint}
        </p>
      ) : null}
    </div>
  );
}

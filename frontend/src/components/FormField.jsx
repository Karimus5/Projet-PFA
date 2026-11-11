/** Champ de formulaire avec libellé et message d'erreur associé (accessible). */
export default function FormField({ id, label, error, hint, className = '', ...inputProps }) {
  const messageId = error ? `${id}-erreur` : hint ? `${id}-aide` : undefined;
  return (
    <div className={className}>
      <label htmlFor={id} className="mb-1 block text-sm font-medium text-slate-700">
        {label}
      </label>
      <input
        id={id}
        name={id}
        className={`input ${error ? 'input-error' : ''}`}
        aria-invalid={error ? 'true' : undefined}
        aria-describedby={messageId}
        {...inputProps}
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

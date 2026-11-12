import { ACCEPT, MAX_FILES_PER_CLAIM } from '../../claims/attachmentRules';
import { formatSize } from '../../utils/format';

/**
 * Sélection de fichiers (PDF, PNG, JPG ; 5 Mo ; 5 par réclamation). Les fichiers choisis sont
 * gérés par le parent (files / onChange) ; errors = messages de validateFiles.
 */
export default function FilePicker({ id, label, files, onChange, errors = [], alreadyAttached = 0, disabled }) {
  const remaining = MAX_FILES_PER_CLAIM - alreadyAttached;

  function handleSelect(event) {
    const selected = Array.from(event.target.files ?? []);
    onChange([...files, ...selected]);
    event.target.value = ''; // permet de rechoisir le même fichier
  }

  return (
    <div>
      <label htmlFor={id} className="mb-1 block text-sm font-medium text-slate-700">
        {label}
      </label>
      <input
        id={id}
        type="file"
        multiple
        accept={ACCEPT}
        disabled={disabled}
        onChange={handleSelect}
        aria-describedby={`${id}-aide`}
        className="block w-full text-sm text-slate-700 file:mr-3 file:rounded-md file:border-0 file:bg-indigo-50 file:px-3 file:py-2 file:text-sm file:font-medium file:text-indigo-700 hover:file:bg-indigo-100"
      />
      <p id={`${id}-aide`} className="mt-1 text-xs text-slate-500">
        PDF, PNG ou JPG · 5 Mo maximum par fichier · {remaining} fichier{remaining > 1 ? 's' : ''} au plus.
      </p>

      {files.length > 0 && (
        <ul className="mt-2 space-y-1" aria-label="Fichiers sélectionnés">
          {files.map((file, index) => (
            <li key={`${file.name}-${index}`} className="flex items-center justify-between rounded border border-slate-200 px-3 py-1.5 text-sm">
              <span className="truncate">
                {file.name} <span className="text-slate-500">({formatSize(file.size)})</span>
              </span>
              <button
                type="button"
                className="text-xs font-medium text-red-600 hover:underline"
                onClick={() => onChange(files.filter((_, i) => i !== index))}
                aria-label={`Retirer ${file.name}`}
              >
                Retirer
              </button>
            </li>
          ))}
        </ul>
      )}

      {errors.length > 0 && (
        <ul role="alert" className="mt-2 space-y-0.5 text-sm text-red-600">
          {errors.map((message) => (
            <li key={message}>{message}</li>
          ))}
        </ul>
      )}
    </div>
  );
}

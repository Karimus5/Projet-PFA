/** Pagination « Précédente / Suivante » (page numérotée à partir de 0 côté API). */
export default function Pagination({ page, totalPages, totalElements, onChange, disabled }) {
  if (totalPages <= 1) {
    return totalElements > 0 ? (
      <p className="text-sm text-slate-500">{totalElements} réclamation{totalElements > 1 ? 's' : ''}</p>
    ) : null;
  }
  return (
    <nav className="flex items-center justify-between gap-4 text-sm" aria-label="Pagination">
      <p className="text-slate-500">
        Page {page + 1} sur {totalPages} · {totalElements} réclamations
      </p>
      <div className="flex gap-2">
        <button type="button" className="btn-secondary" disabled={disabled || page <= 0} onClick={() => onChange(page - 1)}>
          Précédente
        </button>
        <button
          type="button"
          className="btn-secondary"
          disabled={disabled || page >= totalPages - 1}
          onClick={() => onChange(page + 1)}
        >
          Suivante
        </button>
      </div>
    </nav>
  );
}

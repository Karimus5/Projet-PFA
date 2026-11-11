export function Spinner({ className = 'h-5 w-5' }) {
  return (
    <span
      aria-hidden="true"
      className={`inline-block animate-spin rounded-full border-2 border-current border-t-transparent ${className}`}
    />
  );
}

export default function PageLoader({ label = 'Chargement…' }) {
  return (
    <div role="status" className="flex items-center justify-center gap-3 py-16 text-slate-500">
      <Spinner />
      <span>{label}</span>
    </div>
  );
}

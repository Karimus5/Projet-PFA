/**
 * Badge d'une réclamation classée automatiquement par le service IA.
 * variant « short » : « IA » (listes, agents et admins) ; « long » : « Classée automatiquement » (client).
 */
export default function AiBadge({ variant = 'short' }) {
  const long = variant === 'long';
  return (
    <span
      className="inline-flex items-center gap-1 rounded-full bg-fuchsia-100 dark:bg-fuchsia-900/50 px-2 py-0.5 text-xs font-medium text-fuchsia-800 dark:text-fuchsia-200"
      title="Catégorie et service proposés automatiquement par l’IA"
      aria-label={long ? undefined : 'Classée automatiquement par l’IA'}
    >
      <svg aria-hidden="true" viewBox="0 0 16 16" className="h-3 w-3" fill="currentColor">
        <path d="M8 1l1.6 3.9L13.5 6 9.6 7.6 8 11.5 6.4 7.6 2.5 6l3.9-1.1L8 1zm4.5 8l.7 1.8 1.8.7-1.8.7-.7 1.8-.7-1.8-1.8-.7 1.8-.7.7-1.8z" />
      </svg>
      {long ? 'Classée automatiquement' : 'IA'}
    </span>
  );
}

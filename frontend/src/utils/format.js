const dateFormatter = new Intl.DateTimeFormat('fr-FR', { dateStyle: 'long' });

export function formatDate(isoString) {
  if (!isoString) return '—';
  const date = new Date(isoString);
  return Number.isNaN(date.getTime()) ? '—' : dateFormatter.format(date);
}

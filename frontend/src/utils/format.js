const dateFormatter = new Intl.DateTimeFormat('fr-FR', { dateStyle: 'long' });
const dateTimeFormatter = new Intl.DateTimeFormat('fr-FR', { dateStyle: 'medium', timeStyle: 'short' });

function parse(isoString) {
  if (!isoString) return null;
  const date = new Date(isoString);
  return Number.isNaN(date.getTime()) ? null : date;
}

export function formatDate(isoString) {
  const date = parse(isoString);
  return date ? dateFormatter.format(date) : '—';
}

export function formatDateTime(isoString) {
  const date = parse(isoString);
  return date ? dateTimeFormatter.format(date) : '—';
}

/** Taille lisible : 512 o, 12,3 Ko, 4,5 Mo. */
export function formatSize(bytes) {
  if (bytes == null) return '—';
  if (bytes < 1024) return `${bytes} o`;
  const number = new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 1 });
  if (bytes < 1024 * 1024) return `${number.format(bytes / 1024)} Ko`;
  return `${number.format(bytes / (1024 * 1024))} Mo`;
}

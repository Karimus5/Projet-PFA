/**
 * Entrées de la barre de navigation, filtrées selon le rôle de l'utilisateur.
 * available: false = emplacement réservé pour une page future (affiché « Bientôt », non cliquable).
 * Pour activer une page plus tard : lui donner un chemin `to` et passer available à true.
 */
export const NAV_ITEMS = [
  { label: 'Mon profil', to: '/profil', roles: ['CLIENT', 'AGENT', 'ADMIN'], available: true },

  // Réclamations (étapes suivantes : claim-service, stats-service)
  { label: 'Mes réclamations', roles: ['CLIENT'], available: false }, // BF6-BF9
  { label: 'Réclamations du service', roles: ['AGENT'], available: false }, // BF11-BF15
  { label: 'Réclamations', roles: ['ADMIN'], available: false }, // BF23

  { label: 'Utilisateurs', to: '/admin/utilisateurs', roles: ['ADMIN'], available: true }, // BF21
  { label: 'Statistiques', roles: ['ADMIN'], available: false }, // BF24-BF26
];

export function navItemsFor(role) {
  return NAV_ITEMS.filter((item) => item.roles.includes(role));
}

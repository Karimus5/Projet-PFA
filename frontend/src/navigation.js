/**
 * Entrées de la barre de navigation, filtrées selon le rôle de l'utilisateur.
 * available: false = emplacement réservé pour une page future (affiché « Bientôt », non cliquable).
 * Pour activer une page plus tard : lui donner un chemin `to` et passer available à true.
 */
export const NAV_ITEMS = [
  // Réclamations : même page, contenu selon le rôle (BF6-BF9, BF11-BF15, BF23)
  // exclude : sous-chemin qui ne doit pas mettre l'entrée en surbrillance (il a sa propre entrée).
  { label: 'Mes réclamations', to: '/reclamations', roles: ['CLIENT'], available: true, exclude: '/reclamations/nouvelle' },
  { label: 'Nouvelle réclamation', to: '/reclamations/nouvelle', roles: ['CLIENT'], available: true },
  { label: 'Réclamations du service', to: '/reclamations', roles: ['AGENT'], available: true },
  { label: 'Réclamations', to: '/reclamations', roles: ['ADMIN'], available: true },

  { label: 'Référentiels', to: '/admin/referentiels', roles: ['ADMIN'], available: true }, // BF22
  { label: 'Utilisateurs', to: '/admin/utilisateurs', roles: ['ADMIN'], available: true }, // BF21
  { label: 'Statistiques', roles: ['ADMIN'], available: false }, // BF24-BF26

  { label: 'Mon profil', to: '/profil', roles: ['CLIENT', 'AGENT', 'ADMIN'], available: true },
];

export function navItemsFor(role) {
  return NAV_ITEMS.filter((item) => item.roles.includes(role));
}

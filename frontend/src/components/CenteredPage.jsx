/**
 * Centre son contenu (largeur max. max-w-md) dans l'espace entre l'en-tête et le pied de page.
 * Utilisé par les pages de connexion et d'inscription uniquement.
 *
 * flex-1 remplit la hauteur de <main> (colonne flex, voir Layout) ; le centrage passe par des
 * marges automatiques (m-auto) et non par justify-center : si le contenu est plus haut que
 * l'écran, les marges tombent à 0 et la page défile normalement, sans couper le haut de la carte.
 */
export default function CenteredPage({ children }) {
  return (
    <div className="flex flex-1 flex-col">
      <div className="m-auto w-full max-w-md">{children}</div>
    </div>
  );
}

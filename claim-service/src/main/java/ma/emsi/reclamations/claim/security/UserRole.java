package ma.emsi.reclamations.claim.security;

/**
 * Rôles définis par auth-service, transmis par la gateway dans X-User-Role, plus {@link #SYSTEM}.
 *
 * <p>{@code SYSTEM} est INTERNE à claim-service : auteur des actions automatiques (classement par
 * le service IA) dans l'historique et les commentaires internes. Il n'est jamais accepté dans
 * l'en-tête X-User-Role ({@link CurrentUserArgumentResolver}) et ne voit aucune réclamation.
 */
public enum UserRole {
    CLIENT,
    AGENT,
    ADMIN,
    SYSTEM;

    public boolean isStaff() {
        return this == AGENT || this == ADMIN;
    }

    /** Rôle qu'un utilisateur réel peut porter (et donc recevoir de la gateway). */
    public boolean isUserRole() {
        return this != SYSTEM;
    }
}

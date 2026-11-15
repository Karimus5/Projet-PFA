package ma.emsi.reclamations.claim.security;

/**
 * Appelant de la requête, reconstruit à partir des en-têtes X-User-* posés par la gateway
 * (voir {@link CurrentUserArgumentResolver}). Paramètre injectable dans les contrôleurs.
 */
public record CurrentUser(Long id, String email, UserRole role) {

    /** Auteur des actions automatiques (id 0, jamais attribué par auth-service). */
    public static final CurrentUser SYSTEM = new CurrentUser(0L, null, UserRole.SYSTEM);

    public boolean isClient() {
        return role == UserRole.CLIENT;
    }

    public boolean isAgent() {
        return role == UserRole.AGENT;
    }

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }
}

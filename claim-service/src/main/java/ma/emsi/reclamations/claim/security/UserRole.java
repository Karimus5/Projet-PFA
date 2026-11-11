package ma.emsi.reclamations.claim.security;

/** Rôles définis par auth-service, transmis par la gateway dans X-User-Role. */
public enum UserRole {
    CLIENT,
    AGENT,
    ADMIN;

    public boolean isStaff() {
        return this == AGENT || this == ADMIN;
    }
}

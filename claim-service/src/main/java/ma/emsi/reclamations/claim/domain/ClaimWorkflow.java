package ma.emsi.reclamations.claim.domain;

import java.util.Map;
import java.util.Set;
import ma.emsi.reclamations.claim.error.ApiException;
import ma.emsi.reclamations.claim.security.UserRole;

/**
 * Règles de transition : quelles transitions existent ({@link ClaimStatus#canTransitionTo}) et
 * quel rôle peut les déclencher. Vérification dans cet ordre :
 * <ol>
 *   <li>transition absente du diagramme d'états → 409 ;</li>
 *   <li>transition existante mais rôle non autorisé → 403.</li>
 * </ol>
 * La visibilité de la réclamation (404) est vérifiée AVANT, par {@code ClaimAccessPolicy}.
 */
public final class ClaimWorkflow {

    private static final Set<UserRole> STAFF = Set.of(UserRole.AGENT, UserRole.ADMIN);

    /** Rôles autorisés par transition (clé « ANCIEN>NOUVEAU »). */
    private static final Map<String, Set<UserRole>> ALLOWED = Map.of(
            key(ClaimStatus.NEW, ClaimStatus.CLASSIFIED), Set.of(UserRole.ADMIN),   // tri manuel (sans IA)
            key(ClaimStatus.NEW, ClaimStatus.REJECTED), Set.of(UserRole.ADMIN),     // demande invalide
            key(ClaimStatus.CLASSIFIED, ClaimStatus.ASSIGNED), STAFF,               // affectation
            key(ClaimStatus.ASSIGNED, ClaimStatus.IN_PROGRESS), STAFF,              // prise en charge
            key(ClaimStatus.IN_PROGRESS, ClaimStatus.RESOLVED), STAFF,              // résolution
            key(ClaimStatus.RESOLVED, ClaimStatus.CLOSED), Set.of(UserRole.CLIENT), // validée par le client
            key(ClaimStatus.RESOLVED, ClaimStatus.IN_PROGRESS), Set.of(UserRole.CLIENT)); // rouverte

    private ClaimWorkflow() {
    }

    /** Lève 409 si la transition n'existe pas, 403 si le rôle ne peut pas la déclencher. */
    public static void requireTransition(ClaimStatus from, ClaimStatus to, UserRole role) {
        if (!from.canTransitionTo(to)) {
            throw ApiException.conflict("Transition interdite : " + from + " → " + to);
        }
        if (!isAllowed(from, to, role)) {
            throw ApiException.forbidden(switch (to) {
                case CLOSED -> "Seul le client peut clôturer sa réclamation";
                case IN_PROGRESS -> from == ClaimStatus.RESOLVED
                        ? "Seul le client peut rouvrir sa réclamation"
                        : "Seul un agent ou un administrateur peut prendre en charge une réclamation";
                case CLASSIFIED, REJECTED -> "Seul un administrateur peut trier les nouvelles réclamations";
                default -> "Action non autorisée pour votre rôle";
            });
        }
    }

    public static boolean isAllowed(ClaimStatus from, ClaimStatus to, UserRole role) {
        return from.canTransitionTo(to) && ALLOWED.getOrDefault(key(from, to), Set.of()).contains(role);
    }

    private static String key(ClaimStatus from, ClaimStatus to) {
        return from + ">" + to;
    }
}

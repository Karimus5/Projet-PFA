package ma.emsi.reclamations.claim.access;

import java.util.Collection;
import java.util.Objects;
import ma.emsi.reclamations.claim.domain.Claim;
import ma.emsi.reclamations.claim.security.CurrentUser;

/**
 * Règles de visibilité des réclamations (logique pure, sans base de données) :
 * <ul>
 *   <li>CLIENT : uniquement ses propres réclamations ;</li>
 *   <li>AGENT : les réclamations dont le service fait partie de ses services (association
 *       agent-service gérée par l'admin) OU qui lui sont affectées ;</li>
 *   <li>ADMIN : toutes.</li>
 * </ul>
 * Une réclamation non visible est traitée comme inexistante (404, jamais 403), partout.
 * Les commentaires internes ne sont visibles que des agents et des admins.
 */
public final class ClaimAccessPolicy {

    private ClaimAccessPolicy() {
    }

    /**
     * @param agentServiceIds services auxquels l'appelant est rattaché (ignoré si ce n'est pas un agent)
     */
    public static boolean canView(Claim claim, CurrentUser user, Collection<Long> agentServiceIds) {
        return switch (user.role()) {
            case ADMIN -> true;
            case CLIENT -> Objects.equals(claim.getClientId(), user.id());
            case AGENT -> Objects.equals(claim.getAssignedAgentId(), user.id())
                    || (claim.getService() != null && agentServiceIds.contains(claim.getService().getId()));
        };
    }

    public static boolean canSeeInternalComments(CurrentUser user) {
        return user.role().isStaff();
    }
}

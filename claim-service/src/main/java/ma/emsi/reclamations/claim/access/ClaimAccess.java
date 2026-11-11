package ma.emsi.reclamations.claim.access;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import ma.emsi.reclamations.claim.domain.Claim;
import ma.emsi.reclamations.claim.domain.ClaimRepository;
import ma.emsi.reclamations.claim.domain.ClaimStatus;
import ma.emsi.reclamations.claim.domain.ServiceAgentRepository;
import ma.emsi.reclamations.claim.error.ApiException;
import ma.emsi.reclamations.claim.security.CurrentUser;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/** Applique {@link ClaimAccessPolicy} avec les données de la base (chargement unitaire et listes). */
@Component
public class ClaimAccess {

    private final ClaimRepository claims;
    private final ServiceAgentRepository serviceAgents;

    public ClaimAccess(ClaimRepository claims, ServiceAgentRepository serviceAgents) {
        this.claims = claims;
        this.serviceAgents = serviceAgents;
    }

    /** Réclamation visible par l'appelant, sinon 404 (qu'elle existe ou non). */
    public Claim loadVisible(Long claimId, CurrentUser user) {
        Claim claim = claims.findById(claimId).orElseThrow(() -> ApiException.claimNotFound(claimId));
        if (!ClaimAccessPolicy.canView(claim, user, agentServiceIds(user))) {
            throw ApiException.claimNotFound(claimId);
        }
        return claim;
    }

    public List<Long> agentServiceIds(CurrentUser user) {
        return user.isAgent() ? serviceAgents.findServiceIdsByAgentId(user.id()) : List.of();
    }

    /** Filtre de liste équivalent à {@link ClaimAccessPolicy#canView}, plus un filtre de statut facultatif. */
    public Specification<Claim> visibleTo(CurrentUser user, ClaimStatus status) {
        List<Long> serviceIds = agentServiceIds(user);
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            switch (user.role()) {
                case CLIENT -> predicates.add(cb.equal(root.get("clientId"), user.id()));
                case AGENT -> {
                    Predicate assignedToMe = cb.equal(root.get("assignedAgentId"), user.id());
                    predicates.add(serviceIds.isEmpty()
                            ? assignedToMe
                            : cb.or(assignedToMe, root.join("service", JoinType.LEFT).get("id").in(serviceIds)));
                }
                case ADMIN -> {
                    // aucune restriction
                }
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}

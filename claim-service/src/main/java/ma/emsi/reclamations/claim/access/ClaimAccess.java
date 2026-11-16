package ma.emsi.reclamations.claim.access;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;
import ma.emsi.reclamations.claim.domain.AiPrediction;
import ma.emsi.reclamations.claim.domain.AiPredictionOutcome;
import ma.emsi.reclamations.claim.domain.Claim;
import ma.emsi.reclamations.claim.domain.ClaimRepository;
import ma.emsi.reclamations.claim.domain.ClaimStatus;
import ma.emsi.reclamations.claim.domain.ClassificationSource;
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

    /** Statuts où une classification IA peut encore être confirmée ou corrigée (cf. ClaimService). */
    public static final List<ClaimStatus> REVIEWABLE_STATUSES =
            List.of(ClaimStatus.CLASSIFIED, ClaimStatus.ASSIGNED, ClaimStatus.IN_PROGRESS);

    /**
     * Filtre « à confirmer » (BF14) : classée par l'IA (source AI, prédiction APPLIED), ni confirmée
     * ni corrigée, et encore modifiable. Mêmes conditions que ClaimService.confirmClassification.
     * À combiner avec {@link #visibleTo} (périmètre de l'appelant).
     */
    public static Specification<Claim> awaitingAiReview() {
        return (root, query, cb) -> {
            Subquery<Long> pending = query.subquery(Long.class);
            Root<AiPrediction> prediction = pending.from(AiPrediction.class);
            pending.select(prediction.get("id")).where(
                    cb.equal(prediction.get("claim"), root),
                    cb.equal(prediction.get("outcome"), AiPredictionOutcome.APPLIED),
                    cb.isNull(prediction.get("decision")));
            return cb.and(
                    cb.equal(root.get("classificationSource"), ClassificationSource.AI),
                    root.get("status").in(REVIEWABLE_STATUSES),
                    cb.exists(pending));
        };
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
                case SYSTEM -> predicates.add(cb.disjunction()); // rôle interne : aucune réclamation
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}

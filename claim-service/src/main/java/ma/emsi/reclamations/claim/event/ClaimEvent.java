package ma.emsi.reclamations.claim.event;

import java.time.Instant;
import ma.emsi.reclamations.claim.domain.Claim;
import ma.emsi.reclamations.claim.domain.ClaimStatus;
import ma.emsi.reclamations.claim.security.CurrentUser;
import ma.emsi.reclamations.claim.security.UserRole;

/**
 * Événement métier publié APRÈS validation de la transaction. Ne contient que des identifiants et
 * des statuts (pas de texte de réclamation ni de commentaire) : c'est au consommateur de relire
 * ce dont il a besoin.
 */
public record ClaimEvent(
        ClaimEventType type,
        Long claimId,
        Long clientId,
        ClaimStatus previousStatus,
        ClaimStatus status,
        Long categoryId,
        Long serviceId,
        Long assignedAgentId,
        Long actorId,
        UserRole actorRole,
        Instant occurredAt) {

    public static ClaimEvent of(ClaimEventType type, Claim claim, ClaimStatus previousStatus, CurrentUser actor) {
        return new ClaimEvent(type, claim.getId(), claim.getClientId(), previousStatus, claim.getStatus(),
                claim.getCategory() == null ? null : claim.getCategory().getId(),
                claim.getService() == null ? null : claim.getService().getId(),
                claim.getAssignedAgentId(), actor.id(), actor.role(), Instant.now());
    }
}

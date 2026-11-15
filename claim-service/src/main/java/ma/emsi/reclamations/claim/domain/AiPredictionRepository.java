package ma.emsi.reclamations.claim.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AiPredictionRepository extends JpaRepository<AiPrediction, Long> {

    Optional<AiPrediction> findByClaimId(Long claimId);

    long countByOutcome(AiPredictionOutcome outcome);

    long countByDecision(AiFeedbackDecision decision);

    long countByOutcomeAndDecisionIsNull(AiPredictionOutcome outcome);

    /** Retours d'information (confirmations et corrections) pour l'export de réentraînement (BF20). */
    @Query("select p from AiPrediction p join fetch p.claim where p.decision is not null order by p.decidedAt, p.id")
    List<AiPrediction> findAllWithDecision();
}

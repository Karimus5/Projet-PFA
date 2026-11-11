package ma.emsi.reclamations.claim.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    /** Tous les commentaires (agents et admins). */
    List<Comment> findByClaimIdOrderByCreatedAtAscIdAsc(Long claimId);

    /** Commentaires publics uniquement (client) : les internes ne sortent jamais de la base. */
    List<Comment> findByClaimIdAndInternalFalseOrderByCreatedAtAscIdAsc(Long claimId);
}

package ma.emsi.reclamations.claim.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    List<Attachment> findByClaimIdOrderByUploadedAtAscIdAsc(Long claimId);

    long countByClaimId(Long claimId);

    Optional<Attachment> findByIdAndClaimId(Long id, Long claimId);
}

package ma.emsi.reclamations.claim.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StatusHistoryRepository extends JpaRepository<StatusHistory, Long> {

    List<StatusHistory> findByClaimIdOrderByChangedAtAscIdAsc(Long claimId);
}

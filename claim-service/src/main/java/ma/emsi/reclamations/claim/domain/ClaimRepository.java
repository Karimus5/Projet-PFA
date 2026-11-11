package ma.emsi.reclamations.claim.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ClaimRepository extends JpaRepository<Claim, Long>, JpaSpecificationExecutor<Claim> {

    boolean existsByServiceId(Long serviceId);

    boolean existsByCategoryId(Long categoryId);
}

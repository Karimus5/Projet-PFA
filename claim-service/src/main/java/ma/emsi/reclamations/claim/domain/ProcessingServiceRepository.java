package ma.emsi.reclamations.claim.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessingServiceRepository extends JpaRepository<ProcessingService, Long> {

    List<ProcessingService> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}

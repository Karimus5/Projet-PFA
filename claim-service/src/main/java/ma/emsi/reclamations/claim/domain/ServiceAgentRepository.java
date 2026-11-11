package ma.emsi.reclamations.claim.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ServiceAgentRepository extends JpaRepository<ServiceAgent, Long> {

    List<ServiceAgent> findByServiceIdOrderByAgentNameAsc(Long serviceId);

    Optional<ServiceAgent> findByServiceIdAndAgentId(Long serviceId, Long agentId);

    /** Ids des services auxquels l'agent est rattaché (base de sa visibilité). */
    @Query("select sa.service.id from ServiceAgent sa where sa.agentId = :agentId")
    List<Long> findServiceIdsByAgentId(@Param("agentId") Long agentId);
}

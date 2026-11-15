package ma.emsi.reclamations.claim.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    @EntityGraph(attributePaths = "service")
    List<Category> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);

    /** Catégorie proposée par le service IA (nom insensible à la casse), avec son service. */
    @EntityGraph(attributePaths = "service")
    Optional<Category> findFirstByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    boolean existsByServiceId(Long serviceId);
}

package ma.emsi.reclamations.auth.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    /** L'email doit être déjà normalisé (minuscules, sans espaces) : voir EmailNormalizer. */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByRole(Role role);

    List<User> findAllByOrderByIdAsc();
}

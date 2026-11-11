package ma.emsi.reclamations.auth.service;

import java.util.List;
import java.util.Objects;
import ma.emsi.reclamations.auth.error.ApiException;
import ma.emsi.reclamations.auth.user.EmailNormalizer;
import ma.emsi.reclamations.auth.user.Role;
import ma.emsi.reclamations.auth.user.User;
import ma.emsi.reclamations.auth.user.UserRepository;
import ma.emsi.reclamations.auth.web.dto.Dtos.CreateUserRequest;
import ma.emsi.reclamations.auth.web.dto.Dtos.UpdateProfileRequest;
import ma.emsi.reclamations.auth.web.dto.Dtos.UserResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Profil de l'utilisateur connecté (BF4) et administration des comptes (BF21). */
@Service
public class UserService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    // --- Profil (BF4) ---------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public UserResponse getProfile(Long userId) {
        return UserResponse.from(findActive(userId));
    }

    @Transactional
    public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = findActive(userId);

        if (request.name() != null) {
            user.setName(request.name().trim());
        }
        if (request.newPassword() != null) {
            if (request.currentPassword() == null
                    || !passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
                throw ApiException.badRequest("Le mot de passe actuel est incorrect");
            }
            user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        }
        return UserResponse.from(user);
    }

    // --- Administration (BF21) ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<UserResponse> listUsers() {
        return users.findAllByOrderByIdAsc().stream().map(UserResponse::from).toList();
    }

    @Transactional
    public UserResponse createStaffUser(CreateUserRequest request) {
        if (request.role() != Role.AGENT && request.role() != Role.ADMIN) {
            throw ApiException.badRequest("Un administrateur ne peut créer que des comptes AGENT ou ADMIN");
        }
        String email = EmailNormalizer.normalize(request.email());
        if (users.existsByEmail(email)) {
            throw ApiException.emailAlreadyUsed();
        }
        User user = new User(request.name().trim(), email, passwordEncoder.encode(request.password()), request.role());
        return UserResponse.from(users.save(user));
    }

    @Transactional
    public UserResponse changeRole(Long adminId, Long userId, Role role) {
        // Empêche un admin de se retirer ses propres droits (et de bloquer l'administration).
        if (Objects.equals(adminId, userId)) {
            throw ApiException.conflict("Vous ne pouvez pas modifier votre propre rôle");
        }
        User user = find(userId);
        user.setRole(role);
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse setActive(Long adminId, Long userId, boolean active) {
        if (Objects.equals(adminId, userId)) {
            throw ApiException.conflict("Vous ne pouvez pas activer ou désactiver votre propre compte");
        }
        User user = find(userId);
        user.setActive(active);
        return UserResponse.from(user);
    }

    // --- Utilitaires ----------------------------------------------------------------------------

    private User find(Long userId) {
        return users.findById(userId).orElseThrow(() -> ApiException.userNotFound(userId));
    }

    /** Un jeton émis avant la désactivation du compte ne donne plus accès au profil. */
    private User findActive(Long userId) {
        User user = find(userId);
        if (!user.isActive()) {
            throw ApiException.accountDisabled();
        }
        return user;
    }
}

package ma.emsi.reclamations.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import java.time.Duration;
import java.util.Optional;
import ma.emsi.reclamations.auth.error.ApiException;
import ma.emsi.reclamations.auth.security.JwtProperties;
import ma.emsi.reclamations.auth.security.TokenService;
import ma.emsi.reclamations.auth.user.Role;
import ma.emsi.reclamations.auth.user.User;
import ma.emsi.reclamations.auth.user.UserRepository;
import ma.emsi.reclamations.auth.web.dto.Dtos.LoginRequest;
import ma.emsi.reclamations.auth.web.dto.Dtos.RegisterRequest;
import ma.emsi.reclamations.auth.web.dto.Dtos.TokenResponse;
import ma.emsi.reclamations.auth.web.dto.Dtos.UserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/** Tests unitaires d'AuthService : dépôt simulé (Mockito), BCrypt et JWT réels. */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final JwtProperties JWT = new JwtProperties(
            "secret-de-test-unitaire-0123456789-abcdefgh", "test-issuer", Duration.ofMinutes(15));

    @Mock
    private UserRepository users;

    // Coût réduit (4) pour des tests rapides ; 10 par défaut en production.
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    private AuthService authService;

    @BeforeEach
    void setUp() {
        TokenService tokenService = new TokenService(
                new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(JWT.secretKey())), JWT);
        authService = new AuthService(users, passwordEncoder, tokenService);
    }

    // --- Inscription ----------------------------------------------------------------------------

    @Test
    void register_createsClientWithHashedPasswordAndNormalizedEmail() {
        when(users.existsByEmail("alice@example.com")).thenReturn(false);
        when(users.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = authService.register(
                new RegisterRequest("  Alice  ", "  Alice@Example.COM ", "motdepasse123"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        User user = saved.getValue();

        assertThat(user.getRole()).isEqualTo(Role.CLIENT);
        assertThat(user.getEmail()).isEqualTo("alice@example.com");
        assertThat(user.getName()).isEqualTo("Alice");
        assertThat(user.getPasswordHash()).isNotEqualTo("motdepasse123").startsWith("$2");
        assertThat(passwordEncoder.matches("motdepasse123", user.getPasswordHash())).isTrue();
        assertThat(response.role()).isEqualTo(Role.CLIENT);
    }

    @Test
    void register_rejectsDuplicateEmailWith409() {
        when(users.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("Alice", "alice@example.com", "motdepasse123")))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(users, never()).save(any());
    }

    // --- Connexion ------------------------------------------------------------------------------

    @Test
    void login_returnsSignedJwtWithIdEmailAndRole() {
        User user = existingUser(42L, "bob@example.com", "motdepasse123", Role.AGENT, true);
        when(users.findByEmail("bob@example.com")).thenReturn(Optional.of(user));

        TokenResponse response = authService.login(new LoginRequest("BOB@example.com", "motdepasse123"));

        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900);

        Jwt jwt = NimbusJwtDecoder.withSecretKey(JWT.secretKey()).macAlgorithm(MacAlgorithm.HS256).build()
                .decode(response.accessToken());
        assertThat(jwt.getSubject()).isEqualTo("42");
        assertThat(jwt.getClaimAsString("email")).isEqualTo("bob@example.com");
        assertThat(jwt.getClaimAsString("role")).isEqualTo("AGENT");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("test-issuer");
        assertThat(jwt.getExpiresAt()).isAfter(jwt.getIssuedAt());
    }

    @Test
    void login_unknownEmailAndWrongPasswordGiveTheSameError() {
        User user = existingUser(1L, "bob@example.com", "motdepasse123", Role.CLIENT, true);
        when(users.findByEmail("bob@example.com")).thenReturn(Optional.of(user));
        when(users.findByEmail("inconnu@example.com")).thenReturn(Optional.empty());

        ApiException unknownEmail = catchApi(() -> authService.login(new LoginRequest("inconnu@example.com", "motdepasse123")));
        ApiException wrongPassword = catchApi(() -> authService.login(new LoginRequest("bob@example.com", "mauvais-mdp")));

        assertThat(unknownEmail.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(wrongPassword.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unknownEmail.getMessage()).isEqualTo(wrongPassword.getMessage());
    }

    @Test
    void login_refusesDisabledAccount() {
        User user = existingUser(7L, "carl@example.com", "motdepasse123", Role.CLIENT, false);
        when(users.findByEmail("carl@example.com")).thenReturn(Optional.of(user));

        ApiException ex = catchApi(() -> authService.login(new LoginRequest("carl@example.com", "motdepasse123")));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void login_disabledAccountWithWrongPasswordStaysGeneric() {
        // Sans le bon mot de passe, on ne révèle pas que le compte est désactivé.
        User user = existingUser(7L, "carl@example.com", "motdepasse123", Role.CLIENT, false);
        when(users.findByEmail("carl@example.com")).thenReturn(Optional.of(user));

        ApiException ex = catchApi(() -> authService.login(new LoginRequest("carl@example.com", "mauvais-mdp")));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // --- Utilitaires ----------------------------------------------------------------------------

    private User existingUser(Long id, String email, String rawPassword, Role role, boolean active) {
        User user = new User("Test", email, passwordEncoder.encode(rawPassword), role);
        user.setActive(active);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static ApiException catchApi(Runnable action) {
        try {
            action.run();
        } catch (ApiException e) {
            return e;
        }
        throw new AssertionError("ApiException attendue");
    }
}

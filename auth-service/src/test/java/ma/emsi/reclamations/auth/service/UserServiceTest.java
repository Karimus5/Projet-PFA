package ma.emsi.reclamations.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import ma.emsi.reclamations.auth.error.ApiException;
import ma.emsi.reclamations.auth.user.Role;
import ma.emsi.reclamations.auth.user.User;
import ma.emsi.reclamations.auth.user.UserRepository;
import ma.emsi.reclamations.auth.web.dto.Dtos.CreateUserRequest;
import ma.emsi.reclamations.auth.web.dto.Dtos.UpdateProfileRequest;
import ma.emsi.reclamations.auth.web.dto.Dtos.UserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/** Tests unitaires d'UserService : désactivation, rôles, profil. */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final Long ADMIN_ID = 1L;

    @Mock
    private UserRepository users;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(users, passwordEncoder);
    }

    @Test
    void setActive_disablesAnotherAccount() {
        User client = user(5L, Role.CLIENT, true);
        when(users.findById(5L)).thenReturn(Optional.of(client));

        UserResponse response = userService.setActive(ADMIN_ID, 5L, false);

        assertThat(client.isActive()).isFalse();
        assertThat(response.active()).isFalse();
    }

    @Test
    void setActive_refusesToDisableOwnAccount() {
        assertThatThrownBy(() -> userService.setActive(ADMIN_ID, ADMIN_ID, false))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(users, never()).findById(any());
    }

    @Test
    void setActive_unknownUserGives404() {
        when(users.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.setActive(ADMIN_ID, 99L, false))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getProfile_refusedForDisabledAccount() {
        when(users.findById(5L)).thenReturn(Optional.of(user(5L, Role.CLIENT, false)));

        assertThatThrownBy(() -> userService.getProfile(5L))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void changeRole_updatesRole() {
        User client = user(5L, Role.CLIENT, true);
        when(users.findById(5L)).thenReturn(Optional.of(client));

        userService.changeRole(ADMIN_ID, 5L, Role.AGENT);

        assertThat(client.getRole()).isEqualTo(Role.AGENT);
    }

    @Test
    void createStaffUser_refusesClientRole() {
        assertThatThrownBy(() -> userService.createStaffUser(
                new CreateUserRequest("X", "x@example.com", "motdepasse123", Role.CLIENT)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateProfile_changesPasswordOnlyWithCorrectCurrentPassword() {
        User client = user(5L, Role.CLIENT, true);
        when(users.findById(5L)).thenReturn(Optional.of(client));

        assertThatThrownBy(() -> userService.updateProfile(5L,
                new UpdateProfileRequest(null, "mauvais", "nouveau-mdp-123")))
                .isInstanceOf(ApiException.class);
        assertThat(passwordEncoder.matches("motdepasse123", client.getPasswordHash())).isTrue();

        userService.updateProfile(5L, new UpdateProfileRequest("Nouveau Nom", "motdepasse123", "nouveau-mdp-123"));
        assertThat(passwordEncoder.matches("nouveau-mdp-123", client.getPasswordHash())).isTrue();
        assertThat(client.getName()).isEqualTo("Nouveau Nom");
    }

    private User user(Long id, Role role, boolean active) {
        User user = new User("Test", "user" + id + "@example.com", passwordEncoder.encode("motdepasse123"), role);
        user.setActive(active);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}

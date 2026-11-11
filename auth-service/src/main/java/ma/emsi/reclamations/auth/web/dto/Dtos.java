package ma.emsi.reclamations.auth.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import ma.emsi.reclamations.auth.user.Role;
import ma.emsi.reclamations.auth.user.User;

/**
 * Objets échangés par l'API (entrées validées, sorties sans mot de passe).
 * Regroupés dans un seul fichier car ce sont de simples records.
 */
public final class Dtos {

    private Dtos() {
    }

    // --- Entrées --------------------------------------------------------------------------------

    /** BF1 : inscription d'un client. Le rôle n'est PAS un champ : il est imposé (CLIENT). */
    public record RegisterRequest(
            @NotBlank(message = "Le nom est obligatoire")
            @Size(max = 100, message = "Le nom ne doit pas dépasser 100 caractères")
            String name,
            @NotBlank(message = "L'email est obligatoire")
            @Email(message = "L'email n'est pas valide")
            @Size(max = 255, message = "L'email ne doit pas dépasser 255 caractères")
            String email,
            @NotBlank(message = "Le mot de passe est obligatoire")
            @Size(min = 8, max = 72, message = "Le mot de passe doit contenir entre 8 et 72 caractères")
            String password) {
    }

    public record LoginRequest(
            @NotBlank(message = "L'email est obligatoire") String email,
            @NotBlank(message = "Le mot de passe est obligatoire") String password) {
    }

    /** BF4 : champs facultatifs ; changer de mot de passe exige l'ancien (currentPassword). */
    public record UpdateProfileRequest(
            @Size(max = 100, message = "Le nom ne doit pas dépasser 100 caractères")
            @Pattern(regexp = ".*\\S.*", message = "Le nom ne peut pas être vide")
            String name,
            String currentPassword,
            @Size(min = 8, max = 72, message = "Le mot de passe doit contenir entre 8 et 72 caractères")
            String newPassword) {
    }

    /** BF21 : création par un admin d'un compte AGENT ou ADMIN. */
    public record CreateUserRequest(
            @NotBlank(message = "Le nom est obligatoire")
            @Size(max = 100, message = "Le nom ne doit pas dépasser 100 caractères")
            String name,
            @NotBlank(message = "L'email est obligatoire")
            @Email(message = "L'email n'est pas valide")
            @Size(max = 255, message = "L'email ne doit pas dépasser 255 caractères")
            String email,
            @NotBlank(message = "Le mot de passe est obligatoire")
            @Size(min = 8, max = 72, message = "Le mot de passe doit contenir entre 8 et 72 caractères")
            String password,
            @NotNull(message = "Le rôle est obligatoire (AGENT ou ADMIN)")
            Role role) {
    }

    public record ChangeRoleRequest(@NotNull(message = "Le rôle est obligatoire") Role role) {
    }

    public record ChangeStatusRequest(@NotNull(message = "Le champ active est obligatoire") Boolean active) {
    }

    // --- Sorties --------------------------------------------------------------------------------

    /** Représentation publique d'un utilisateur : jamais de mot de passe ni de hachage. */
    public record UserResponse(Long id, String name, String email, Role role, boolean active, Instant createdAt) {

        public static UserResponse from(User user) {
            return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole(),
                    user.isActive(), user.getCreatedAt());
        }
    }

    public record TokenResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {
    }
}

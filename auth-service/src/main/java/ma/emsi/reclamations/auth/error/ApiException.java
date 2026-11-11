package ma.emsi.reclamations.auth.error;

import org.springframework.http.HttpStatus;

/** Erreur métier portant son code HTTP ; transformée en ApiError par GlobalExceptionHandler. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    // --- Fabriques pour les cas courants -----------------------------------------------------

    /** Message volontairement identique pour « email inconnu » et « mauvais mot de passe ». */
    public static ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "Email ou mot de passe incorrect");
    }

    public static ApiException accountDisabled() {
        return new ApiException(HttpStatus.FORBIDDEN, "Ce compte est désactivé");
    }

    public static ApiException emailAlreadyUsed() {
        return new ApiException(HttpStatus.CONFLICT, "Cet email est déjà utilisé");
    }

    public static ApiException userNotFound(Long id) {
        return new ApiException(HttpStatus.NOT_FOUND, "Utilisateur introuvable : " + id);
    }

    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }
}

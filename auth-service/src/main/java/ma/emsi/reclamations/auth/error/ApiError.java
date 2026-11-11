package ma.emsi.reclamations.auth.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Format JSON UNIQUE de toutes les erreurs du service (400, 401, 403, 404, 409, 500).
 * fieldErrors n'est présent que pour les erreurs de validation (champ → message).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fieldErrors) {

    public static ApiError of(HttpStatus status, String message, String path) {
        return new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message, path, null);
    }

    public static ApiError validation(String message, String path, Map<String, String> fieldErrors) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message, path, fieldErrors);
    }
}

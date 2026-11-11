package ma.emsi.reclamations.claim.error;

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

    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, message);
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }

    /**
     * Réclamation inexistante OU non visible par l'appelant : même réponse 404 dans les deux cas,
     * pour ne jamais révéler l'existence d'une réclamation d'un autre client.
     */
    public static ApiException claimNotFound(Long id) {
        return notFound("Réclamation introuvable : " + id);
    }
}

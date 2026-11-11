package ma.emsi.reclamations.claim.security;

import ma.emsi.reclamations.claim.error.ApiException;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Identifie l'appelant à partir des en-têtes posés par l'api-gateway APRÈS vérification du JWT :
 * X-User-Id (sub), X-User-Email, X-User-Role (CLIENT | AGENT | ADMIN). La gateway supprime tout
 * en-tête X-User-* envoyé par le client avant d'ajouter les siens.
 *
 * <p><b>Limite assumée</b> : claim-service ne revalide pas le JWT et fait confiance à ces
 * en-têtes. Son port (8082) ne doit donc JAMAIS être exposé directement : n'importe qui pouvant
 * l'appeler sans passer par la gateway pourrait se faire passer pour un autre utilisateur.
 *
 * <p>En-têtes absents ou invalides → 401 (ApiError).
 */
@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    public static final String USER_ID = "X-User-Id";
    public static final String USER_EMAIL = "X-User-Email";
    public static final String USER_ROLE = "X-User-Role";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return CurrentUser.class.equals(parameter.getParameterType());
    }

    @Override
    public CurrentUser resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                       NativeWebRequest request, WebDataBinderFactory binderFactory) {
        String id = request.getHeader(USER_ID);
        String role = request.getHeader(USER_ROLE);
        if (id == null || id.isBlank() || role == null || role.isBlank()) {
            throw ApiException.unauthorized("Authentification requise");
        }
        try {
            long userId = Long.parseLong(id.trim());
            if (userId <= 0) {
                throw ApiException.unauthorized("Identité de l'appelant invalide");
            }
            return new CurrentUser(userId, request.getHeader(USER_EMAIL), UserRole.valueOf(role.trim()));
        } catch (IllegalArgumentException e) { // NumberFormatException incluse
            throw ApiException.unauthorized("Identité de l'appelant invalide");
        }
    }
}

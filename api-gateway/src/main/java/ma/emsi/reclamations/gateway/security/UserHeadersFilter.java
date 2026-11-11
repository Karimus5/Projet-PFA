package ma.emsi.reclamations.gateway.security;

import java.util.List;
import java.util.Locale;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Propage l'identité vérifiée aux services en aval, sur TOUTES les routes :
 * <ol>
 *   <li>supprime tout en-tête X-User-* envoyé par le client (anti-usurpation d'identité) ;</li>
 *   <li>si le jeton a été validé, ajoute X-User-Id (sub), X-User-Email et X-User-Role.</li>
 * </ol>
 * L'en-tête Authorization est conservé : les services peuvent revérifier le jeton eux-mêmes.
 */
@Component
public class UserHeadersFilter implements GlobalFilter, Ordered {

    public static final String USER_ID = "X-User-Id";
    public static final String USER_EMAIL = "X-User-Email";
    public static final String USER_ROLE = "X-User-Role";
    private static final String USER_HEADER_PREFIX = "x-user-";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(JwtAuthenticationToken.class::isInstance)
                .map(auth -> ((JwtAuthenticationToken) auth).getToken())
                .map(jwt -> withIdentity(exchange, jwt))
                .switchIfEmpty(Mono.fromSupplier(() -> withIdentity(exchange, null)))
                .flatMap(chain::filter);
    }

    private static ServerWebExchange withIdentity(ServerWebExchange exchange, Jwt jwt) {
        return exchange.mutate().request(request -> request.headers(headers -> {
            removeClientUserHeaders(headers);
            if (jwt != null) {
                setIfPresent(headers, USER_ID, jwt.getSubject());
                setIfPresent(headers, USER_EMAIL, jwt.getClaimAsString("email"));
                setIfPresent(headers, USER_ROLE, jwt.getClaimAsString(SecurityConfig.ROLE_CLAIM));
            }
        })).build();
    }

    private static void removeClientUserHeaders(HttpHeaders headers) {
        List<String> toRemove = headers.keySet().stream()
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(USER_HEADER_PREFIX))
                .toList();
        toRemove.forEach(headers::remove);
    }

    private static void setIfPresent(HttpHeaders headers, String name, String value) {
        if (value != null && !value.isBlank()) {
            headers.set(name, value);
        }
    }

    @Override
    public int getOrder() {
        // Avant tous les filtres de routage (le jeton est déjà validé par Spring Security).
        return Ordered.HIGHEST_PRECEDENCE;
    }
}

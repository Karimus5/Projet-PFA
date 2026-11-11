package ma.emsi.reclamations.gateway.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Trace chaque requête reçue par la passerelle : méthode, chemin, statut HTTP et durée.
 *
 * <p>Sécurité : seuls la méthode et le chemin (sans query string) sont écrits. Aucun en-tête
 * (Authorization, X-User-*) ni paramètre n'est journalisé : le jeton n'apparaît jamais dans les logs.
 * Placé avant Spring Security, il trace aussi les 401/403 de la passerelle.
 *
 * <p>C'est un {@link WebFilter} (et non un GlobalFilter de Gateway) pour couvrir aussi les
 * requêtes non routées (actuator, 404) et les erreurs (503 service indisponible). Le statut
 * est lu juste avant l'envoi de la réponse ({@code beforeCommit}), donc c'est le statut final,
 * y compris quand il est fixé par le gestionnaire d'erreurs.
 */
@Component
public class RequestLoggingFilter implements WebFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        long start = System.nanoTime();
        ServerHttpRequest request = exchange.getRequest();

        exchange.getResponse().beforeCommit(() -> {
            HttpStatusCode status = exchange.getResponse().getStatusCode();
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            log.info("{} {} -> {} ({} ms)",
                    request.getMethod(),
                    request.getURI().getRawPath(),
                    status != null ? status.value() : "-",
                    durationMs);
            return Mono.empty();
        });

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}

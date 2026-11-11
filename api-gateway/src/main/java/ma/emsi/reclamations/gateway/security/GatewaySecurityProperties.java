package ma.emsi.reclamations.gateway.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Règles d'accès de la passerelle, définies dans config-repo/api-gateway.yml (préfixe gateway.security).
 *
 * @param publicRoutes routes accessibles sans jeton
 * @param roleRules    routes réservées à certains rôles (évaluées dans l'ordre)
 */
@ConfigurationProperties(prefix = "gateway.security")
public record GatewaySecurityProperties(List<RouteRule> publicRoutes, List<RouteRule> roleRules) {

    public GatewaySecurityProperties {
        publicRoutes = publicRoutes == null ? List.of() : List.copyOf(publicRoutes);
        roleRules = roleRules == null ? List.of() : List.copyOf(roleRules);
    }

    /**
     * @param method méthode HTTP (GET, POST...) ou null pour toutes
     * @param path   motif de chemin Spring (ex. /api/auth/admin/**)
     * @param roles  rôles autorisés (CLIENT, AGENT, ADMIN) ; ignoré pour les routes publiques
     */
    public record RouteRule(String method, String path, List<String> roles) {

        public RouteRule {
            roles = roles == null ? List.of() : List.copyOf(roles);
        }
    }
}

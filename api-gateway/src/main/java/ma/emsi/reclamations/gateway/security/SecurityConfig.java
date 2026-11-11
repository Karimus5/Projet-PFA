package ma.emsi.reclamations.gateway.security;

import ma.emsi.reclamations.gateway.security.GatewaySecurityProperties.RouteRule;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.config.GlobalCorsProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity.AuthorizeExchangeSpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * Sécurité de la passerelle (Spring Security réactif) : filtre global exécuté AVANT le routage.
 * <ol>
 *   <li>pré-requêtes CORS (OPTIONS) et routes publiques : sans jeton ;</li>
 *   <li>règles par rôle (config-repo/api-gateway.yml) : jeton valide + rôle, sinon 403 ;</li>
 *   <li>tout le reste : jeton valide (signature HS256, expiration, émetteur), sinon 401.</li>
 * </ol>
 * Sans état (aucune session) ; CSRF désactivé car le jeton voyage dans l'en-tête Authorization.
 */
@Configuration
@EnableWebFluxSecurity
@EnableConfigurationProperties({JwtProperties.class, GatewaySecurityProperties.class})
public class SecurityConfig {

    static final String ROLE_CLAIM = "role";

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http,
                                                  GatewaySecurityProperties rules,
                                                  JsonSecurityErrorHandler errorHandler,
                                                  CorsConfigurationSource corsConfigurationSource) {
        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                // Même configuration CORS que la passerelle : les réponses 401/403 produites ici
                // portent aussi les en-têtes CORS (sinon le frontend ne pourrait pas les lire).
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .authorizeExchange(exchanges -> applyRules(exchanges, rules))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler));
        return http.build();
    }

    private static void applyRules(AuthorizeExchangeSpec exchanges, GatewaySecurityProperties rules) {
        exchanges.pathMatchers(HttpMethod.OPTIONS).permitAll();
        for (RouteRule rule : rules.publicRoutes()) {
            match(exchanges, rule).permitAll();
        }
        for (RouteRule rule : rules.roleRules()) {
            match(exchanges, rule).hasAnyRole(rule.roles().toArray(String[]::new));
        }
        exchanges.anyExchange().authenticated();
    }

    private static AuthorizeExchangeSpec.Access match(AuthorizeExchangeSpec exchanges, RouteRule rule) {
        return rule.method() == null || rule.method().isBlank()
                ? exchanges.pathMatchers(rule.path())
                : exchanges.pathMatchers(HttpMethod.valueOf(rule.method().trim().toUpperCase()), rule.path());
    }

    /** Même vérification que auth-service : signature HS256 + expiration + émetteur. */
    @Bean
    ReactiveJwtDecoder reactiveJwtDecoder(JwtProperties properties) {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withSecretKey(properties.secretKey())
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
        return decoder;
    }

    /** Réutilise spring.cloud.gateway.server.webflux.globalcors (config-repo/api-gateway.yml). */
    @Bean
    CorsConfigurationSource corsConfigurationSource(GlobalCorsProperties globalCors) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        globalCors.getCorsConfigurations().forEach(source::registerCorsConfiguration);
        return source;
    }

    /** Claim "role" (ex. ADMIN) → autorité ROLE_ADMIN, utilisée par hasAnyRole(...). */
    private static ReactiveJwtAuthenticationConverterAdapter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(ROLE_CLAIM);
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }
}

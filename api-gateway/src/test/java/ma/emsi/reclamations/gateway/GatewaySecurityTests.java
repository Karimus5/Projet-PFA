package ma.emsi.reclamations.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Vérification du JWT à la passerelle. « auth-service » est un serveur WireMock factice qui
 * répond 200 à tout : si la passerelle laisse passer, on reçoit 200 ; si elle bloque, on reçoit
 * son 401/403 JSON et WireMock n'est jamais appelé. Les en-têtes réellement reçus en aval sont
 * vérifiés côté WireMock.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT) // config : src/test/resources/application.yml
class GatewaySecurityTests {

    private static final WireMockServer authService = new WireMockServer(wireMockConfig().dynamicPort());

    static {
        authService.start();
    }

    @DynamicPropertySource
    static void discoveryInstances(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.discovery.client.simple.instances.auth-service[0].uri",
                () -> "http://localhost:" + authService.port());
    }

    @AfterAll
    static void stopWireMock() {
        authService.stop();
    }

    @Autowired
    private WebTestClient webClient;

    @BeforeEach
    void stubAuthService() {
        authService.resetAll();
        authService.stubFor(any(urlPathMatching("/api/auth/.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"source\":\"auth-service-factice\"}")));
    }

    // --- Jeton valide / absent / invalide -------------------------------------------------------

    @Test
    void validToken_isForwardedWithIdentityHeaders() {
        String token = TestTokens.valid(42, "client@test.local", "CLIENT");

        webClient.get().uri("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.source").isEqualTo("auth-service-factice");

        authService.verify(getRequestedFor(urlEqualTo("/api/auth/me"))
                .withHeader("X-User-Id", equalTo("42"))
                .withHeader("X-User-Email", equalTo("client@test.local"))
                .withHeader("X-User-Role", equalTo("CLIENT"))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer " + token)));
    }

    @Test
    void missingToken_gives401Json() {
        expect401("/api/auth/me", null, "Authentification requise");
    }

    @Test
    void expiredToken_gives401() {
        expect401("/api/auth/me", TestTokens.expired(42, "client@test.local", "CLIENT"), "Jeton invalide ou expiré");
    }

    @Test
    void forgedSignature_gives401() {
        expect401("/api/auth/me", TestTokens.forged(42, "client@test.local", "ADMIN"), "Jeton invalide ou expiré");
    }

    @Test
    void wrongIssuer_gives401() {
        expect401("/api/auth/me", TestTokens.wrongIssuer(42, "client@test.local", "CLIENT"), "Jeton invalide ou expiré");
    }

    @Test
    void malformedToken_gives401() {
        expect401("/api/auth/me", "pas.un.jwt", "Jeton invalide ou expiré");
    }

    // --- Rôles ----------------------------------------------------------------------------------

    @Test
    void clientOnAdminRoute_gives403Json() {
        webClient.get().uri("/api/auth/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.valid(42, "client@test.local", "CLIENT"))
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(403)
                .jsonPath("$.error").isEqualTo("Forbidden")
                .jsonPath("$.message").isEqualTo("Accès refusé : droits insuffisants")
                .jsonPath("$.path").isEqualTo("/api/auth/admin/users");

        authService.verify(0, anyRequestedFor(urlPathMatching(".*")));
    }

    @Test
    void agentOnAdminRoute_gives403() {
        webClient.get().uri("/api/auth/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.valid(7, "agent@test.local", "AGENT"))
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void adminOnAdminRoute_isForwarded() {
        webClient.get().uri("/api/auth/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.valid(1, "admin@test.local", "ADMIN"))
                .exchange()
                .expectStatus().isOk();

        authService.verify(getRequestedFor(urlEqualTo("/api/auth/admin/users"))
                .withHeader("X-User-Role", equalTo("ADMIN")));
    }

    // --- Routes publiques -----------------------------------------------------------------------

    @Test
    void registerAndLogin_arePublic() {
        webClient.post().uri("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}").exchange().expectStatus().isOk();
        webClient.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}").exchange().expectStatus().isOk();
    }

    @Test
    void publicRuleIsMethodSpecific() {
        // Seul POST /api/auth/login est public : un GET exige un jeton.
        expect401("/api/auth/login", null, "Authentification requise");
    }

    @Test
    void healthIsPublic() {
        webClient.get().uri("/actuator/health").exchange().expectStatus().isOk();
    }

    @Test
    void corsPreflightWithoutToken_isAllowed() {
        webClient.method(HttpMethod.OPTIONS).uri("/api/auth/admin/users")
                .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000");
    }

    // --- Anti-usurpation des en-têtes X-User-* --------------------------------------------------

    @Test
    void spoofedUserHeaders_areOverwrittenByTokenIdentity() {
        String token = TestTokens.valid(42, "client@test.local", "CLIENT");

        webClient.get().uri("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("X-User-Id", "1")
                .header("X-User-Role", "ADMIN")
                .header("x-user-email", "admin@test.local")
                .header("X-User-Custom", "pirate")
                .exchange()
                .expectStatus().isOk();

        authService.verify(getRequestedFor(urlEqualTo("/api/auth/me"))
                .withHeader("X-User-Id", equalTo("42"))
                .withHeader("X-User-Role", equalTo("CLIENT"))
                .withHeader("X-User-Email", equalTo("client@test.local"))
                .withHeader("X-User-Custom", absent()));
    }

    @Test
    void spoofedUserHeaders_areRemovedOnPublicRoutes() {
        webClient.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .header("X-User-Id", "1")
                .header("X-User-Role", "ADMIN")
                .header("X-User-Email", "admin@test.local")
                .bodyValue("{}")
                .exchange()
                .expectStatus().isOk();

        authService.verify(postRequestedFor(urlEqualTo("/api/auth/login"))
                .withHeader("X-User-Id", absent())
                .withHeader("X-User-Role", absent())
                .withHeader("X-User-Email", absent()));
    }

    @Test
    void spoofedRoleHeaderDoesNotGrantAdminAccess() {
        webClient.get().uri("/api/auth/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.valid(42, "client@test.local", "CLIENT"))
                .header("X-User-Role", "ADMIN")
                .exchange()
                .expectStatus().isForbidden();
    }

    // --- Utilitaires ----------------------------------------------------------------------------

    private void expect401(String path, String token, String message) {
        WebTestClient.RequestHeadersSpec<?> request = webClient.get().uri(path);
        if (token != null) {
            request = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        request.exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.error").isEqualTo("Unauthorized")
                .jsonPath("$.message").isEqualTo(message)
                .jsonPath("$.path").isEqualTo(path)
                .jsonPath("$.timestamp").exists();

        authService.verify(0, anyRequestedFor(urlPathMatching(".*")));
    }
}

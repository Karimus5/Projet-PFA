package ma.emsi.reclamations.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Routage, CORS, actuator et journalisation de la passerelle, SANS Config Server ni Eureka :
 * <ul>
 *   <li>routes, CORS et règles de sécurité : vrai fichier ../config-repo/api-gateway.yml ;</li>
 *   <li>« claim-service » pointe vers un serveur WireMock factice (SimpleDiscoveryClient) ;</li>
 *   <li>« stats-service » n'a aucune instance, pour vérifier la réponse 503.</li>
 * </ul>
 * Les scénarios JWT détaillés sont dans GatewaySecurityTests.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT) // config : src/test/resources/application.yml
@ExtendWith(OutputCaptureExtension.class)
class ApiGatewayApplicationTests {

    private static final WireMockServer claimService = new WireMockServer(wireMockConfig().dynamicPort());

    static {
        claimService.start();
    }

    @DynamicPropertySource
    static void discoveryInstances(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.discovery.client.simple.instances.claim-service[0].uri",
                () -> "http://localhost:" + claimService.port());
    }

    @AfterAll
    static void stopWireMock() {
        claimService.stop();
    }

    @Autowired
    private WebTestClient webClient;

    private final String clientToken = TestTokens.valid(10, "client@test.local", "CLIENT");

    @BeforeEach
    void resetStubs() {
        claimService.resetAll();
    }

    @Test
    void contextLoads() {
        // Le contexte Spring (réactif) démarre.
    }

    @Test
    void routesClaimsRequestToClaimService() {
        claimService.stubFor(get(urlEqualTo("/api/claims/test"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"source\":\"claim-service-factice\"}")));

        webClient.get().uri("/api/claims/test")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + clientToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.source").isEqualTo("claim-service-factice");

        // Le chemin est transmis tel quel au service en aval.
        claimService.verify(getRequestedFor(urlEqualTo("/api/claims/test")));
    }

    @Test
    void returns503WhenServiceHasNoInstance() {
        webClient.get().uri("/api/stats/test")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + clientToken)
                .exchange()
                .expectStatus().isEqualTo(503);
    }

    @Test
    void corsPreflightAllowedForReactFrontendWithoutToken() {
        webClient.method(HttpMethod.OPTIONS).uri("/api/claims/test")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization, Content-Type")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173");
    }

    @Test
    void corsPreflightRejectedForUnknownOrigin() {
        webClient.method(HttpMethod.OPTIONS).uri("/api/claims/test")
                .header(HttpHeaders.ORIGIN, "http://evil.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void unauthorizedResponseCarriesCorsHeadersForFrontend() {
        // Sans en-têtes CORS sur le 401, le navigateur masquerait l'erreur au frontend React.
        webClient.get().uri("/api/claims/test")
                .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000");
    }

    @Test
    void healthIsPublicAndUp() {
        webClient.get().uri("/actuator/health")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void logsMethodPathAndStatusButNeverTheToken(CapturedOutput output) {
        webClient.get().uri("/api/stats/log-test")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + clientToken)
                .exchange()
                .expectStatus().isEqualTo(503);
        webClient.get().uri("/api/claims/log-test-401").exchange().expectStatus().isUnauthorized();

        assertThat(output)
                .contains("GET /api/stats/log-test -> 503")
                .contains("GET /api/claims/log-test-401 -> 401")
                .doesNotContain(clientToken);
    }
}

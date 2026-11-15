package ma.emsi.reclamations.claim.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.io.IOException;
import java.net.ServerSocket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Service IA ARRÊTÉ : l'URL configurée pointe vers un port où rien n'écoute. Délais et disjoncteur
 * de production (connexion 500 ms, lecture 1 500 ms, ouverture après 3 échecs sur 5).
 */
@SpringBootTest
@AutoConfigureMockMvc
class AiClassifierStoppedTest {

    @DynamicPropertySource
    static void aiProperties(DynamicPropertyRegistry registry) throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort(); // libéré à la fermeture : plus rien n'y écoute
        }
        registry.add("ai-classifier.enabled", () -> "true");
        registry.add("ai-classifier.url", () -> "http://127.0.0.1:" + closedPort);
        registry.add("ai-classifier.api-key", () -> "cle-de-test-claim-service-0123");
        String cb = "resilience4j.circuitbreaker.instances.ai-classifier.";
        registry.add(cb + "sliding-window-size", () -> "5");
        registry.add(cb + "minimum-number-of-calls", () -> "3");
        registry.add(cb + "failure-rate-threshold", () -> "50");
        registry.add(cb + "wait-duration-in-open-state", () -> "30s");
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private CircuitBreakerRegistry circuitBreakers;

    @BeforeEach
    void resetCircuit() {
        circuitBreakers.circuitBreaker("ai-classifier").reset();
    }

    @Test
    void creationStillWorksWhenAiServiceIsStopped() throws Exception {
        long start = System.nanoTime();
        String body = mvc.perform(post("/api/claims").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Colis\",\"description\":\"Mon colis n'est jamais arrivé\"}")
                        .header("X-User-Id", "90001").header("X-User-Role", "CLIENT"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.comments", hasSize(0)))
                .andReturn().getResponse().getContentAsString();
        assertThat((System.nanoTime() - start) / 1_000_000).as("durée de création").isLessThan(3_000);

        long id = ((Number) JsonPath.read(body, "$.id")).longValue();
        mvc.perform(get("/api/claims/" + id).header("X-User-Id", "90002").header("X-User-Role", "ADMIN"))
                .andExpect(jsonPath("$.aiPrediction.failureReason").value("UNREACHABLE"))
                .andExpect(jsonPath("$.comments[0].content", containsString("service injoignable ou arrêté")));
    }

    @Test
    void circuitOpensAfterThreeFailuresThenAnswersImmediately() throws Exception {
        for (int i = 0; i < 3; i++) {
            createAsClient(90_100 + i);
        }
        assertThat(circuitBreakers.circuitBreaker("ai-classifier").getState()).isEqualTo(CircuitBreaker.State.OPEN);

        long start = System.nanoTime();
        String body = createAsClient(90_200);
        assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(1_000);
        long id = ((Number) JsonPath.read(body, "$.id")).longValue();
        mvc.perform(get("/api/claims/" + id).header("X-User-Id", "90002").header("X-User-Role", "ADMIN"))
                .andExpect(jsonPath("$.aiPrediction.failureReason").value("CIRCUIT_OPEN"));
    }

    private String createAsClient(long clientId) throws Exception {
        return mvc.perform(post("/api/claims").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Colis\",\"description\":\"Colis perdu\"}")
                        .header("X-User-Id", String.valueOf(clientId)).header("X-User-Role", "CLIENT"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("NEW"))
                .andReturn().getResponse().getContentAsString();
    }
}

package ma.emsi.reclamations.claim.web;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.jayway.jsonpath.JsonPath;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import ma.emsi.reclamations.claim.security.UserRole;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Classement automatique à la création (BF16-BF19), confirmation / correction (BF14) et export
 * (BF20). Le service IA est un serveur WireMock factice : aucun test n'appelle le vrai service.
 * Délais raccourcis (lecture 400 ms) et disjoncteur réduit pour des tests rapides.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AiClassificationIntegrationTest {

    private static final String API_KEY = "cle-de-test-claim-service-0123";
    private static final WireMockServer aiService = new WireMockServer(wireMockConfig().dynamicPort());
    private static final AtomicLong IDS = new AtomicLong(50_000);

    static {
        aiService.start();
    }

    @DynamicPropertySource
    static void aiProperties(DynamicPropertyRegistry registry) {
        registry.add("ai-classifier.enabled", () -> "true");
        registry.add("ai-classifier.url", aiService::baseUrl);
        registry.add("ai-classifier.api-key", () -> API_KEY);
        registry.add("ai-classifier.connect-timeout", () -> "300ms");
        registry.add("ai-classifier.read-timeout", () -> "400ms");
        String cb = "resilience4j.circuitbreaker.instances.ai-classifier.";
        registry.add(cb + "sliding-window-size", () -> "4");
        registry.add(cb + "minimum-number-of-calls", () -> "2");
        registry.add(cb + "failure-rate-threshold", () -> "50");
        registry.add(cb + "wait-duration-in-open-state", () -> "60s");
    }

    @AfterAll
    static void stopAiService() {
        aiService.stop();
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private CircuitBreakerRegistry circuitBreakers;

    private Caller admin;
    private Caller client;
    private Caller deliveryAgent;
    private Caller billingAgent;
    private long billingCategoryId;

    @BeforeEach
    void setUp() throws Exception {
        aiService.resetAll();
        circuitBreakers.circuitBreaker("ai-classifier").reset();
        admin = caller(UserRole.ADMIN);
        client = caller(UserRole.CLIENT);
        deliveryAgent = caller(UserRole.AGENT);
        billingAgent = caller(UserRole.AGENT);
        billingCategoryId = idByName(admin.on(get("/api/claims/categories")), "Facturation");
        addAgent(idByName(admin.on(get("/api/claims/services")), "Livraison"), deliveryAgent, "Agent Livraison");
        addAgent(idByName(admin.on(get("/api/claims/services")), "Facturation"), billingAgent, "Agent Facturation");
    }

    // --- Création : classement automatique ou repli ---------------------------------------------

    @Test
    void highConfidenceClassifiesAutomatically() throws Exception {
        stubAi(200, aiAnswer("Livraison", 0.9234, true));

        String body = create("Colis", "Mon colis n'est jamais arrivé")
                .andExpect(jsonPath("$.status").value("CLASSIFIED"))
                .andExpect(jsonPath("$.category.name").value("Livraison"))
                .andExpect(jsonPath("$.service.name").value("Livraison"))
                .andExpect(jsonPath("$.autoClassified").value(true))
                .andExpect(jsonPath("$.history", hasSize(2)))
                .andExpect(jsonPath("$.history[1].oldStatus").value("NEW"))
                .andExpect(jsonPath("$.history[1].newStatus").value("CLASSIFIED"))
                .andExpect(jsonPath("$.history[1].authorRole").value("SYSTEM"))
                .andExpect(jsonPath("$.history[1].note").value("Classement automatique : Livraison"))
                .andExpect(jsonPath("$.comments", hasSize(0)))
                .andReturn().getResponse().getContentAsString();

        // Appel au service IA : clé partagée + titre et description.
        aiService.verify(1, postRequestedFor(urlEqualTo("/classify"))
                .withHeader("X-API-Key", equalTo(API_KEY))
                .withRequestBody(equalToJson("{\"title\":\"Colis\",\"description\":\"Mon colis n'est jamais arrivé\"}")));

        admin.on(get("/api/claims/" + id(body)))
                .andExpect(jsonPath("$.classificationSource").value("AI"))
                .andExpect(jsonPath("$.confidenceScore").value(0.9234))
                .andExpect(jsonPath("$.autoClassified").value(true))
                .andExpect(jsonPath("$.aiPrediction.outcome").value("APPLIED"))
                .andExpect(jsonPath("$.aiPrediction.predictedCategory").value("Livraison"))
                .andExpect(jsonPath("$.aiPrediction.modelVersion").value("1.0.0"))
                .andExpect(jsonPath("$.aiPrediction.threshold").value(0.57))
                .andExpect(jsonPath("$.aiPrediction.decision").doesNotExist());
        // Visible de l'agent du service Livraison (service renseigné automatiquement).
        deliveryAgent.on(get("/api/claims/" + id(body))).andExpect(status().isOk());
    }

    @Test
    void lowConfidenceStaysNewWithInternalTrace() throws Exception {
        stubAi(200, aiAnswer("Support technique", 0.52, false));

        long id = id(create("bonjour", "bonjour")
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.category").doesNotExist())
                .andExpect(jsonPath("$.classificationSource").doesNotExist())
                .andExpect(jsonPath("$.history", hasSize(1)))
                .andExpect(jsonPath("$.comments", hasSize(0))) // trace interne invisible du client
                .andReturn().getResponse().getContentAsString());

        admin.on(get("/api/claims/" + id))
                .andExpect(jsonPath("$.comments", hasSize(1)))
                .andExpect(jsonPath("$.comments[0].internal").value(true))
                .andExpect(jsonPath("$.comments[0].authorRole").value("SYSTEM"))
                .andExpect(jsonPath("$.comments[0].content",
                        containsString("confiance insuffisante (Support technique, 52 % < 57 %)")))
                .andExpect(jsonPath("$.aiPrediction.outcome").value("LOW_CONFIDENCE"));
    }

    @Test
    void unknownCategoryStaysNew() throws Exception {
        stubAi(200, aiAnswer("Garantie", 0.97, true));
        long id = id(create("Garantie", "Mon téléphone est en panne").andExpect(jsonPath("$.status").value("NEW"))
                .andReturn().getResponse().getContentAsString());
        admin.on(get("/api/claims/" + id))
                .andExpect(jsonPath("$.comments[0].content", containsString("« Garantie » n'existe pas")))
                .andExpect(jsonPath("$.aiPrediction.outcome").value("UNKNOWN_CATEGORY"));
    }

    @Test
    void serverErrorStaysNew() throws Exception {
        stubAi(500, "{\"detail\":\"boom\"}");
        expectManual(create("Colis", "Colis perdu"), "SERVER_ERROR", "erreur du service IA");
    }

    @Test
    void rejectedApiKeyStaysNew() throws Exception {
        stubAi(401, "{\"detail\":\"Clé API absente ou invalide\"}");
        expectManual(create("Colis", "Colis perdu"), "REJECTED", "appel refusé");
    }

    @Test
    void invalidAnswerStaysNew() throws Exception {
        stubAi(200, aiAnswer("Livraison", 1.7, true)); // confiance hors de [0, 1]
        expectManual(create("Colis", "Colis perdu"), "INVALID_RESPONSE", "réponse du service IA invalide");
    }

    @Test
    void timeoutStaysNewQuickly() throws Exception {
        aiService.stubFor(post(urlEqualTo("/classify")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(aiAnswer("Livraison", 0.9, true)).withFixedDelay(2_500)));
        long start = System.nanoTime();
        ResultActions created = create("Colis", "Colis perdu");
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        expectManual(created, "TIMEOUT", "délai de réponse dépassé");
        assertThat(elapsedMs).as("création malgré un service IA trop lent").isLessThan(2_000);
    }

    @Test
    void circuitBreakerOpensAfterFailuresAndSkipsCalls() throws Exception {
        stubAi(500, "{}");
        create("Colis 1", "Colis perdu").andExpect(jsonPath("$.status").value("NEW"));
        create("Colis 2", "Colis perdu").andExpect(jsonPath("$.status").value("NEW"));
        assertThat(circuitBreakers.circuitBreaker("ai-classifier").getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // Disjoncteur ouvert : la création suivante n'appelle plus le service IA.
        expectManual(create("Colis 3", "Colis perdu"), "CIRCUIT_OPEN", "désactivé temporairement");
        aiService.verify(2, postRequestedFor(urlEqualTo("/classify")));
    }

    @Test
    void lowConfidenceIsNotAFailureForTheCircuitBreaker() throws Exception {
        stubAi(200, aiAnswer("Livraison", 0.30, false));
        for (int i = 0; i < 4; i++) {
            create("bonjour " + i, "bonjour").andExpect(jsonPath("$.status").value("NEW"));
        }
        assertThat(circuitBreakers.circuitBreaker("ai-classifier").getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        aiService.verify(4, postRequestedFor(urlEqualTo("/classify")));
    }

    // --- Confirmation et correction (BF14) -------------------------------------------------------

    @Test
    void agentConfirmsAiClassification() throws Exception {
        long id = autoClassifiedDeliveryClaim();

        deliveryAgent.on(MockMvcRequestBuilders.post("/api/claims/" + id + "/classification/confirmation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLASSIFIED"))
                .andExpect(jsonPath("$.classificationSource").value("AI"))
                .andExpect(jsonPath("$.aiPrediction.decision").value("CONFIRMED"))
                .andExpect(jsonPath("$.aiPrediction.finalCategory").value("Livraison"))
                .andExpect(jsonPath("$.aiPrediction.decidedByRole").value("AGENT"))
                .andExpect(jsonPath("$.aiPrediction.decidedByName").value("Agent Livraison"))
                .andExpect(jsonPath("$.comments[*].content", hasItem("Classification IA confirmée : Livraison, service Livraison")));

        // Déjà confirmée → 409 ; client → 403 ; agent d'un autre service (non visible) → 404.
        deliveryAgent.on(MockMvcRequestBuilders.post("/api/claims/" + id + "/classification/confirmation"))
                .andExpect(status().isConflict());
        client.on(MockMvcRequestBuilders.post("/api/claims/" + id + "/classification/confirmation"))
                .andExpect(status().isForbidden());
        billingAgent.on(MockMvcRequestBuilders.post("/api/claims/" + id + "/classification/confirmation"))
                .andExpect(status().isNotFound());
    }

    @Test
    void confirmingAManualClassificationIsAConflict() throws Exception {
        stubAi(200, aiAnswer("Livraison", 0.40, false));
        long id = id(create("bonjour", "bonjour").andReturn().getResponse().getContentAsString());
        admin.on(json(put("/api/claims/" + id + "/classification"), "{\"categoryId\":" + billingCategoryId + "}"))
                .andExpect(status().isOk());
        admin.on(MockMvcRequestBuilders.post("/api/claims/" + id + "/classification/confirmation"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cette réclamation n'a pas été classée par l'IA"));
    }

    @Test
    void agentCorrectsAiClassificationAndExportContainsIt() throws Exception {
        String before = admin.on(get("/api/claims/admin/ai-stats")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long id = autoClassifiedDeliveryClaim();

        deliveryAgent.on(json(put("/api/claims/" + id + "/classification"), "{\"categoryId\":" + billingCategoryId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLASSIFIED")) // statut conservé
                .andExpect(jsonPath("$.category.name").value("Facturation"))
                .andExpect(jsonPath("$.classificationSource").value("MANUAL"))
                .andExpect(jsonPath("$.aiPrediction.decision").value("CORRECTED"))
                .andExpect(jsonPath("$.aiPrediction.predictedCategory").value("Livraison"))
                .andExpect(jsonPath("$.aiPrediction.finalCategory").value("Facturation"))
                .andExpect(jsonPath("$.aiPrediction.finalService").value("Facturation"))
                .andExpect(jsonPath("$.comments[*].content", hasItem(containsString("(proposition IA : Livraison, 92 %)"))));

        String csv = admin.on(get("/api/claims/admin/ai-feedback/export"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Disposition", containsString("ai-feedback-")))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        List<String> lines = csv.lines().toList();
        assertThat(lines.get(0)).isEqualTo("claim_id,title,description,predicted_category,predicted_service,"
                + "confidence,final_category,final_service,decision,model_version,decided_by_role,decided_at");
        assertThat(lines).anyMatch(line -> line.startsWith("\"" + id + "\",\"Colis\",\"Mon colis n'est jamais arrivé\","
                + "\"Livraison\",\"Livraison\",\"0.9234\",\"Facturation\",\"Facturation\",\"CORRECTED\",\"1.0.0\",\"AGENT\","));
        assertThat(csv).doesNotContain("@test.local"); // aucun e-mail (client ou agent)

        String after = admin.on(get("/api/claims/admin/ai-stats")).andReturn().getResponse().getContentAsString();
        assertThat(count(after, "corrected")).isEqualTo(count(before, "corrected") + 1);
        assertThat(count(after, "autoClassified")).isEqualTo(count(before, "autoClassified") + 1);
    }

    @Test
    void statsCountManualFallbacks() throws Exception {
        String before = admin.on(get("/api/claims/admin/ai-stats")).andReturn().getResponse().getContentAsString();
        stubAi(200, aiAnswer("Livraison", 0.40, false));
        create("bonjour", "bonjour");
        stubAi(500, "{}");
        create("Colis", "Colis perdu");
        String after = admin.on(get("/api/claims/admin/ai-stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correctionRate").exists())
                .andReturn().getResponse().getContentAsString();
        assertThat(count(after, "sentToManual")).isEqualTo(count(before, "sentToManual") + 2);
        assertThat(count(after, "lowConfidence")).isEqualTo(count(before, "lowConfidence") + 1);
        assertThat(count(after, "unavailable")).isEqualTo(count(before, "unavailable") + 1);
    }

    @Test
    void exportAndStatsAreAdminOnly() throws Exception {
        for (Caller notAdmin : List.of(client, deliveryAgent)) {
            notAdmin.on(get("/api/claims/admin/ai-feedback/export")).andExpect(status().isForbidden());
            notAdmin.on(get("/api/claims/admin/ai-stats")).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/claims/admin/ai-stats")).andExpect(status().isUnauthorized());
    }

    @Test
    void systemRoleIsRefusedFromHeaders() throws Exception {
        mvc.perform(get("/api/claims").header("X-User-Id", "0").header("X-User-Role", "SYSTEM"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/claims").header("X-User-Id", "7").header("X-User-Role", "SYSTEM"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void clientNeverSeesAiFailureDetails() throws Exception {
        stubAi(500, "{}");
        long id = id(create("Colis", "Colis perdu").andReturn().getResponse().getContentAsString());
        client.on(get("/api/claims/" + id))
                .andExpect(jsonPath("$.comments", hasSize(0)))
                .andExpect(jsonPath("$.aiPrediction").doesNotExist())
                .andExpect(content().string(not(containsString("service IA"))));
    }

    // --- Étape 7c : aucune donnée du modèle pour le client, filtre « à confirmer », seuil, auteur -

    @Test
    void clientReceivesNoModelDataAnywhere() throws Exception {
        long id = autoClassifiedDeliveryClaim();

        String detail = client.on(get("/api/claims/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category.name").value("Livraison"))
                .andExpect(jsonPath("$.service.name").value("Livraison"))
                .andExpect(jsonPath("$.autoClassified").value(true))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        String list = client.on(get("/api/claims"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].autoClassified").value(true))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        for (String json : List.of(detail, list)) {
            assertThat(json).doesNotContain("classificationSource", "confidenceScore", "aiPrediction",
                    "predictedCategory", "modelVersion", "threshold", "0.9234", "1.0.0", "%");
        }
    }

    @Test
    void staffKeepsModelDataInListAndDetail() throws Exception {
        long id = autoClassifiedDeliveryClaim();
        deliveryAgent.on(get("/api/claims"))
                .andExpect(jsonPath("$.content[?(@.id == " + id + ")].classificationSource", hasItem("AI")))
                .andExpect(jsonPath("$.content[?(@.id == " + id + ")].confidenceScore", hasItem(0.9234)))
                .andExpect(jsonPath("$.content[?(@.id == " + id + ")].autoClassified", hasItem(true)));
        deliveryAgent.on(get("/api/claims/" + id))
                .andExpect(jsonPath("$.aiPrediction.confidence").value(0.9234))
                .andExpect(jsonPath("$.aiPrediction.threshold").value(0.57));
    }

    @Test
    void thresholdIsStoredForLowConfidenceToo() throws Exception {
        stubAi(200, aiAnswer("Livraison", 0.40, false));
        long id = id(create("bonjour", "bonjour").andReturn().getResponse().getContentAsString());
        admin.on(get("/api/claims/" + id))
                .andExpect(jsonPath("$.aiPrediction.outcome").value("LOW_CONFIDENCE"))
                .andExpect(jsonPath("$.aiPrediction.threshold").value(0.57))
                .andExpect(jsonPath("$.autoClassified").value(false));
    }

    @Test
    void toConfirmFilterListsOnlyPendingAiClassificationsInCallerScope() throws Exception {
        long pending = autoClassifiedDeliveryClaim();
        long confirmed = autoClassifiedDeliveryClaim();
        deliveryAgent.on(MockMvcRequestBuilders.post("/api/claims/" + confirmed + "/classification/confirmation"))
                .andExpect(status().isOk());
        long corrected = autoClassifiedDeliveryClaim();
        admin.on(json(put("/api/claims/" + corrected + "/classification"), "{\"categoryId\":" + billingCategoryId + "}"))
                .andExpect(status().isOk());
        stubAi(200, aiAnswer("Livraison", 0.40, false));
        long lowConfidence = id(create("bonjour", "bonjour").andReturn().getResponse().getContentAsString());

        String agentPage = deliveryAgent.on(get("/api/claims").param("toConfirm", "true").param("size", "100"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Number> agentIds = JsonPath.read(agentPage, "$.content[*].id");
        assertThat(agentIds).extracting(Number::longValue).contains(pending)
                .doesNotContain(confirmed, corrected, lowConfidence);

        String adminPage = admin.on(get("/api/claims").param("toConfirm", "true").param("size", "100"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Number> adminIds = JsonPath.read(adminPage, "$.content[*].id");
        assertThat(adminIds).extracting(Number::longValue).contains(pending)
                .doesNotContain(confirmed, corrected, lowConfidence);

        // Hors du périmètre de l'agent Facturation (service Livraison) : absente de sa liste.
        String billingPage = billingAgent.on(get("/api/claims").param("toConfirm", "true").param("size", "100"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Number> billingIds = JsonPath.read(billingPage, "$.content[*].id");
        assertThat(billingIds).extracting(Number::longValue).doesNotContain(pending);

        // Combinable avec le filtre de statut.
        String assignedOnly = admin.on(get("/api/claims").param("toConfirm", "true").param("status", "ASSIGNED"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Number> assignedIds = JsonPath.read(assignedOnly, "$.content[*].id");
        assertThat(assignedIds).extracting(Number::longValue).doesNotContain(pending);
    }

    @Test
    void toConfirmFilterIsForbiddenToClients() throws Exception {
        client.on(get("/api/claims").param("toConfirm", "true"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Le filtre « à confirmer » est réservé aux agents et aux administrateurs"));
        client.on(get("/api/claims").param("toConfirm", "false")).andExpect(status().isOk());
    }

    @Test
    void adminDecisionHasRoleButNoName() throws Exception {
        long id = autoClassifiedDeliveryClaim();
        admin.on(MockMvcRequestBuilders.post("/api/claims/" + id + "/classification/confirmation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiPrediction.decidedByRole").value("ADMIN"))
                .andExpect(jsonPath("$.aiPrediction.decidedByName").doesNotExist()); // nom d'admin inconnu
    }

    @Test
    void agentCorrectionRecordsAgentName() throws Exception {
        long id = autoClassifiedDeliveryClaim();
        String body = deliveryAgent.on(json(put("/api/claims/" + id + "/classification"), "{\"categoryId\":" + billingCategoryId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiPrediction.decision").value("CORRECTED"))
                .andExpect(jsonPath("$.aiPrediction.decidedByName").value("Agent Livraison"))
                .andReturn().getResponse().getContentAsString();
        String predictionJson = JsonPath.parse(body).read("$.aiPrediction").toString();
        assertThat(predictionJson).doesNotContain("@");
    }

    // --- Utilitaires ------------------------------------------------------------------------------

    private long autoClassifiedDeliveryClaim() throws Exception {
        stubAi(200, aiAnswer("Livraison", 0.9234, true));
        return id(create("Colis", "Mon colis n'est jamais arrivé")
                .andExpect(jsonPath("$.status").value("CLASSIFIED"))
                .andReturn().getResponse().getContentAsString());
    }

    private void expectManual(ResultActions created, String reason, String traceExcerpt) throws Exception {
        long id = id(created.andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.comments", hasSize(0)))
                .andReturn().getResponse().getContentAsString());
        admin.on(get("/api/claims/" + id))
                .andExpect(jsonPath("$.aiPrediction.outcome").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.aiPrediction.failureReason").value(reason))
                .andExpect(jsonPath("$.comments[0].internal").value(true))
                .andExpect(jsonPath("$.comments[0].content", containsString(traceExcerpt)));
    }

    private ResultActions create(String title, String description) throws Exception {
        return client.on(json(MockMvcRequestBuilders.post("/api/claims"),
                        "{\"title\":\"" + title + "\",\"description\":\"" + description + "\"}"))
                .andExpect(status().isCreated());
    }

    private static void stubAi(int status, String body) {
        aiService.stubFor(post(urlEqualTo("/classify")).willReturn(aResponse().withStatus(status)
                .withHeader("Content-Type", "application/json").withBody(body)));
    }

    private static String aiAnswer(String category, double confidence, boolean reached) {
        return "{\"category\":\"" + category + "\",\"service\":\"" + category + "\",\"confidence\":" + confidence
                + ",\"threshold\":0.57,\"thresholdReached\":" + reached + ",\"reason\":\"test\","
                + "\"modelVersion\":\"1.0.0\",\"processingTimeMs\":12.5,\"probabilities\":{}}";
    }

    private static long id(String body) {
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private static long count(String statsJson, String field) {
        return ((Number) JsonPath.read(statsJson, "$." + field)).longValue();
    }

    private void addAgent(long serviceId, Caller agent, String name) throws Exception {
        admin.on(json(MockMvcRequestBuilders.post("/api/claims/admin/services/" + serviceId + "/agents"),
                        "{\"agentId\":" + agent.id() + ",\"agentName\":\"" + name + "\"}"))
                .andExpect(status().isCreated());
    }

    private static long idByName(ResultActions list, String name) throws Exception {
        String body = list.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Number> ids = JsonPath.read(body, "$[?(@.name == '" + name + "')].id");
        return ids.get(0).longValue();
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private Caller caller(UserRole role) {
        return new Caller(IDS.incrementAndGet(), role, mvc);
    }

    private record Caller(long id, UserRole role, MockMvc mvc) {

        ResultActions on(MockHttpServletRequestBuilder request) throws Exception {
            return mvc.perform(request
                    .header("X-User-Id", String.valueOf(id))
                    .header("X-User-Email", role.name().toLowerCase() + id + "@test.local")
                    .header("X-User-Role", role.name()));
        }
    }
}

package ma.emsi.reclamations.claim.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import ma.emsi.reclamations.claim.security.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/**
 * Tests d'intégration (H2 mode PostgreSQL, migrations Flyway réelles). La gateway est simulée en
 * posant les en-têtes X-User-Id / X-User-Email / X-User-Role. Chaque test utilise ses propres
 * identifiants (compteur) pour rester indépendant des autres.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ClaimApiIntegrationTest {

    private static final AtomicLong IDS = new AtomicLong(1000);
    private static final byte[] PDF = "%PDF-1.7\n1 0 obj\n<<>>\nendobj\n%%EOF".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    @Autowired
    private MockMvc mvc;

    private Caller admin;
    private Caller client;
    private Caller otherClient;
    private Caller billingAgent;
    private Caller deliveryAgent;
    private long billingServiceId;
    private long deliveryServiceId;
    private long billingCategoryId;
    private long deliveryCategoryId;

    @BeforeEach
    void setUp() throws Exception {
        admin = caller(UserRole.ADMIN);
        client = caller(UserRole.CLIENT);
        otherClient = caller(UserRole.CLIENT);
        billingAgent = caller(UserRole.AGENT);
        deliveryAgent = caller(UserRole.AGENT);

        billingServiceId = idByName(admin.on(get("/api/claims/services")), "Facturation");
        deliveryServiceId = idByName(admin.on(get("/api/claims/services")), "Livraison");
        billingCategoryId = idByName(admin.on(get("/api/claims/categories")), "Facturation");
        deliveryCategoryId = idByName(admin.on(get("/api/claims/categories")), "Livraison");

        addAgent(billingServiceId, billingAgent, "Agent Facturation");
        addAgent(deliveryServiceId, deliveryAgent, "Agent Livraison");
    }

    // --- Identification par les en-têtes de la gateway ------------------------------------------

    @Test
    void missingOrInvalidUserHeadersGive401() throws Exception {
        mvc.perform(get("/api/claims"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Authentification requise"));
        mvc.perform(get("/api/claims").header("X-User-Id", "abc").header("X-User-Role", "CLIENT"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/claims").header("X-User-Id", "5").header("X-User-Role", "ROOT"))
                .andExpect(status().isUnauthorized());
    }

    // --- Client : soumission, liste, détail ------------------------------------------------------

    @Test
    void clientCreatesClaimAndSeesItWithHistory() throws Exception {
        long id = createClaim(client);

        client.on(get("/api/claims/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.clientId").value(client.id()))
                .andExpect(jsonPath("$.category").doesNotExist())
                .andExpect(jsonPath("$.history", hasSize(1)))
                .andExpect(jsonPath("$.history[0].newStatus").value("NEW"));

        client.on(get("/api/claims"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id));
    }

    @Test
    void createValidatesInput() throws Exception {
        client.on(post("/api/claims").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").exists())
                .andExpect(jsonPath("$.fieldErrors.description").exists());
        client.on(post("/api/claims").contentType(MediaType.APPLICATION_JSON).content("pas du json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyClientsCanCreateClaims() throws Exception {
        for (Caller staff : List.of(billingAgent, admin)) {
            staff.on(post("/api/claims").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"t\",\"description\":\"d\"}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void clientNeverSeesAnotherClientsClaim() throws Exception {
        long id = createClaim(client);

        otherClient.on(get("/api/claims/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Réclamation introuvable : " + id));
        otherClient.on(get("/api/claims"))
                .andExpect(jsonPath("$.content[*].id", not(hasItem((int) id))));
        // Même 404 pour toutes les actions : l'existence n'est jamais révélée.
        otherClient.on(json(post("/api/claims/" + id + "/comments"), "{\"content\":\"coucou\"}"))
                .andExpect(status().isNotFound());
        otherClient.on(json(patch("/api/claims/" + id + "/status"), "{\"status\":\"CLOSED\"}"))
                .andExpect(status().isNotFound());
        otherClient.on(json(post("/api/claims/" + id + "/complements"), "{\"text\":\"x\"}"))
                .andExpect(status().isNotFound());
        // Une réclamation inexistante donne exactement la même réponse.
        otherClient.on(get("/api/claims/999999")).andExpect(status().isNotFound());
    }

    @Test
    void listIsPaginatedAndFilterableByStatus() throws Exception {
        long first = createClaim(client);
        createClaim(client);
        long third = createClaim(client);
        admin.on(json(patch("/api/claims/" + first + "/status"), "{\"status\":\"REJECTED\"}"))
                .andExpect(status().isOk());

        client.on(get("/api/claims").param("size", "2"))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].id").value(third))   // plus récentes d'abord
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
        client.on(get("/api/claims").param("status", "REJECTED"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(first));
        client.on(get("/api/claims").param("size", "500")).andExpect(status().isBadRequest());
        client.on(get("/api/claims").param("status", "INCONNU")).andExpect(status().isBadRequest());
    }

    @Test
    void clientCanCompleteAnOpenClaimOnly() throws Exception {
        long id = createClaim(client);
        client.on(json(post("/api/claims/" + id + "/complements"), "{\"text\":\"Numéro de commande : 42\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description", containsString("Numéro de commande : 42")))
                .andExpect(jsonPath("$.description", containsString("Complément du")));
        billingAgent.on(json(post("/api/claims/" + id + "/complements"), "{\"text\":\"x\"}"))
                .andExpect(status().isNotFound()); // NEW : invisible pour un agent
        admin.on(json(post("/api/claims/" + id + "/complements"), "{\"text\":\"x\"}"))
                .andExpect(status().isForbidden());

        admin.on(json(patch("/api/claims/" + id + "/status"), "{\"status\":\"REJECTED\"}")).andExpect(status().isOk());
        client.on(json(post("/api/claims/" + id + "/complements"), "{\"text\":\"trop tard\"}"))
                .andExpect(status().isConflict());
    }

    // --- Cycle de vie complet (critères de réussite) ---------------------------------------------

    @Test
    void fullLifecycleIsRecordedInHistory() throws Exception {
        long id = createClaim(client);

        // Un agent ne voit pas une réclamation non classée.
        billingAgent.on(get("/api/claims/" + id)).andExpect(status().isNotFound());

        // Tri manuel par l'admin → CLASSIFIED, service déduit de la catégorie.
        admin.on(json(put("/api/claims/" + id + "/classification"), "{\"categoryId\":" + billingCategoryId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLASSIFIED"))
                .andExpect(jsonPath("$.classificationSource").value("MANUAL"))
                .andExpect(jsonPath("$.confidenceScore").doesNotExist())
                .andExpect(jsonPath("$.service.id").value(billingServiceId));

        // L'agent du service la voit ; celui d'un autre service non.
        billingAgent.on(get("/api/claims")).andExpect(jsonPath("$.content[*].id", hasItem((int) id)));
        deliveryAgent.on(get("/api/claims/" + id)).andExpect(status().isNotFound());

        // Affectation à l'agent → ASSIGNED.
        admin.on(json(put("/api/claims/" + id + "/assignment"),
                        "{\"serviceId\":" + billingServiceId + ",\"agentId\":" + billingAgent.id() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.assignedAgent.id").value(billingAgent.id()))
                .andExpect(jsonPath("$.assignedAgent.name").value("Agent Facturation"));

        billingAgent.on(json(patch("/api/claims/" + id + "/status"), "{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        billingAgent.on(json(patch("/api/claims/" + id + "/status"), "{\"status\":\"RESOLVED\",\"note\":\"Avoir émis\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RESOLVED"));

        // Seul le client clôture.
        billingAgent.on(json(patch("/api/claims/" + id + "/status"), "{\"status\":\"CLOSED\"}"))
                .andExpect(status().isForbidden());
        admin.on(json(patch("/api/claims/" + id + "/status"), "{\"status\":\"CLOSED\"}"))
                .andExpect(status().isForbidden());
        client.on(json(patch("/api/claims/" + id + "/status"), "{\"status\":\"CLOSED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.history", hasSize(6)))
                .andExpect(jsonPath("$.history[*].newStatus")
                        .value(org.hamcrest.Matchers.contains("NEW", "CLASSIFIED", "ASSIGNED", "IN_PROGRESS",
                                "RESOLVED", "CLOSED")))
                .andExpect(jsonPath("$.history[4].note").value("Avoir émis"))
                .andExpect(jsonPath("$.history[5].authorRole").value("CLIENT"));

        // Réclamation clôturée : plus aucune transition ni commentaire.
        client.on(json(patch("/api/claims/" + id + "/status"), "{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isConflict());
        client.on(json(post("/api/claims/" + id + "/comments"), "{\"content\":\"merci\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void clientCanReopenAResolvedClaim() throws Exception {
        long id = resolvedClaim();
        billingAgent.on(json(patch("/api/claims/" + id + "/status"), "{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Seul le client peut rouvrir sa réclamation"));
        client.on(json(patch("/api/claims/" + id + "/status"), "{\"status\":\"IN_PROGRESS\",\"note\":\"Pas réglé\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void forbiddenTransitionsGive409() throws Exception {
        long id = createClaim(client);
        client.on(json(patch("/api/claims/" + id + "/status"), "{\"status\":\"CLOSED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Transition interdite : NEW → CLOSED"));
        admin.on(json(patch("/api/claims/" + id + "/status"), "{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isConflict());
        // Classement et affectation passent par leurs endpoints dédiés.
        admin.on(json(patch("/api/claims/" + id + "/status"), "{\"status\":\"CLASSIFIED\"}"))
                .andExpect(status().isBadRequest());
        // Affecter une réclamation non classée → 409.
        admin.on(json(put("/api/claims/" + id + "/assignment"), "{\"serviceId\":" + billingServiceId + "}"))
                .andExpect(status().isConflict());
    }

    @Test
    void clientCannotProcessHisOwnClaim() throws Exception {
        long id = assignedClaim();
        client.on(json(patch("/api/claims/" + id + "/status"), "{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isForbidden());
        client.on(json(put("/api/claims/" + id + "/assignment"), "{\"serviceId\":" + billingServiceId + "}"))
                .andExpect(status().isForbidden());
        client.on(json(put("/api/claims/" + id + "/classification"), "{\"categoryId\":" + billingCategoryId + "}"))
                .andExpect(status().isForbidden());
    }

    // --- Commentaires ----------------------------------------------------------------------------

    @Test
    void internalCommentsAreHiddenFromClient() throws Exception {
        long id = assignedClaim();
        billingAgent.on(json(post("/api/claims/" + id + "/comments"), "{\"content\":\"Note interne\",\"internal\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.internal").value(true));
        billingAgent.on(json(post("/api/claims/" + id + "/comments"), "{\"content\":\"Réponse au client\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.internal").value(false));
        client.on(json(post("/api/claims/" + id + "/comments"), "{\"content\":\"Question du client\"}"))
                .andExpect(status().isCreated());

        client.on(get("/api/claims/" + id))
                .andExpect(jsonPath("$.comments[*].content", not(hasItem("Note interne"))))
                .andExpect(jsonPath("$.comments[*].internal", not(hasItem(true))))
                .andExpect(jsonPath("$.comments[*].content", hasItem("Réponse au client")))
                .andExpect(jsonPath("$.comments[*].content", hasItem("Question du client")));
        billingAgent.on(get("/api/claims/" + id))
                .andExpect(jsonPath("$.comments[*].content", hasItem("Note interne")));
        admin.on(get("/api/claims/" + id))
                .andExpect(jsonPath("$.comments[*].content", hasItem("Note interne")));
    }

    @Test
    void clientCannotWriteInternalComment() throws Exception {
        long id = createClaim(client);
        client.on(json(post("/api/claims/" + id + "/comments"), "{\"content\":\"secret\",\"internal\":true}"))
                .andExpect(status().isForbidden());
        client.on(json(post("/api/claims/" + id + "/comments"), "{\"content\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.content").exists());
    }

    // --- Classification et affectation ----------------------------------------------------------

    @Test
    void agentCannotTriageNewClaims() throws Exception {
        long id = createClaim(client);
        billingAgent.on(json(put("/api/claims/" + id + "/classification"), "{\"categoryId\":" + billingCategoryId + "}"))
                .andExpect(status().isNotFound()); // NEW : hors de sa visibilité
    }

    @Test
    void agentCorrectsClassificationWithoutChangingStatus() throws Exception {
        long id = assignedClaim();
        billingAgent.on(json(put("/api/claims/" + id + "/classification"), "{\"categoryId\":" + deliveryCategoryId
                        + ",\"serviceId\":" + billingServiceId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.category.name").value("Livraison"))
                .andExpect(jsonPath("$.service.id").value(billingServiceId))
                .andExpect(jsonPath("$.comments[*].content",
                        hasItem(containsString("Classification corrigée : Facturation → Livraison"))));
        client.on(get("/api/claims/" + id))
                .andExpect(jsonPath("$.comments", hasSize(0))); // la trace est interne
    }

    @Test
    void agentReassignsToAnotherServiceKeepingStatus() throws Exception {
        long id = assignedClaim();
        billingAgent.on(json(patch("/api/claims/" + id + "/status"), "{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk());

        // Réaffectation sans agent d'une réclamation en cours → 400.
        billingAgent.on(json(put("/api/claims/" + id + "/assignment"), "{\"serviceId\":" + deliveryServiceId + "}"))
                .andExpect(status().isBadRequest());
        // Agent non rattaché au service cible → 400.
        billingAgent.on(json(put("/api/claims/" + id + "/assignment"),
                        "{\"serviceId\":" + deliveryServiceId + ",\"agentId\":" + billingAgent.id() + "}"))
                .andExpect(status().isBadRequest());

        billingAgent.on(json(put("/api/claims/" + id + "/assignment"),
                        "{\"serviceId\":" + deliveryServiceId + ",\"agentId\":" + deliveryAgent.id() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.service.id").value(deliveryServiceId))
                .andExpect(jsonPath("$.assignedAgent.id").value(deliveryAgent.id()));

        deliveryAgent.on(get("/api/claims/" + id)).andExpect(status().isOk());
        billingAgent.on(get("/api/claims/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void agentSeesClaimsAssignedToHimOutsideHisServices() throws Exception {
        long id = classifiedClaim(deliveryCategoryId);
        // L'admin rattache l'agent facturation au service livraison, affecte, puis le détache.
        addAgent(deliveryServiceId, billingAgent, "Agent Facturation");
        admin.on(json(put("/api/claims/" + id + "/assignment"),
                "{\"serviceId\":" + deliveryServiceId + ",\"agentId\":" + billingAgent.id() + "}"))
                .andExpect(status().isOk());
        admin.on(delete("/api/claims/admin/services/" + deliveryServiceId + "/agents/" + billingAgent.id()))
                .andExpect(status().isNoContent());

        billingAgent.on(get("/api/claims/" + id)).andExpect(status().isOk());
        billingAgent.on(get("/api/claims")).andExpect(jsonPath("$.content[*].id", hasItem((int) id)));
    }

    // --- Référentiels ----------------------------------------------------------------------------

    @Test
    void referenceDataIsReadableByStaffOnly() throws Exception {
        billingAgent.on(get("/api/claims/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("Support technique")));
        billingAgent.on(get("/api/claims/services/" + billingServiceId + "/agents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].agentId", hasItem((int) billingAgent.id())));
        client.on(get("/api/claims/categories")).andExpect(status().isForbidden());
        client.on(get("/api/claims/services")).andExpect(status().isForbidden());
    }

    @Test
    void adminManagesServicesAndCategories() throws Exception {
        String suffix = String.valueOf(IDS.incrementAndGet());
        String created = admin.on(json(post("/api/claims/admin/services"), "{\"name\":\"Qualité " + suffix + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long serviceId = ((Number) JsonPath.read(created, "$.id")).longValue();

        admin.on(json(post("/api/claims/admin/services"), "{\"name\":\"qualité " + suffix + "\"}"))
                .andExpect(status().isConflict());
        admin.on(json(put("/api/claims/admin/services/" + serviceId),
                        "{\"name\":\"Qualité " + suffix + "\",\"description\":\"Contrôle qualité\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Contrôle qualité"));

        String category = admin.on(json(post("/api/claims/admin/categories"),
                        "{\"name\":\"Produit défectueux " + suffix + "\",\"serviceId\":" + serviceId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.service.id").value(serviceId))
                .andReturn().getResponse().getContentAsString();
        long categoryId = ((Number) JsonPath.read(category, "$.id")).longValue();

        admin.on(json(post("/api/claims/admin/categories"), "{\"name\":\"X" + suffix + "\",\"serviceId\":999999}"))
                .andExpect(status().isBadRequest());
        // Un service qui traite encore une catégorie ne peut pas être supprimé.
        admin.on(delete("/api/claims/admin/services/" + serviceId)).andExpect(status().isConflict());
        admin.on(delete("/api/claims/admin/categories/" + categoryId)).andExpect(status().isNoContent());
        admin.on(delete("/api/claims/admin/services/" + serviceId)).andExpect(status().isNoContent());
        admin.on(delete("/api/claims/admin/services/" + serviceId)).andExpect(status().isNotFound());
    }

    @Test
    void categoryInUseCannotBeDeleted() throws Exception {
        classifiedClaim(billingCategoryId);
        admin.on(delete("/api/claims/admin/categories/" + billingCategoryId)).andExpect(status().isConflict());
    }

    @Test
    void adminEndpointsAreForbiddenToAgents() throws Exception {
        billingAgent.on(json(post("/api/claims/admin/services"), "{\"name\":\"Pirate\"}"))
                .andExpect(status().isForbidden());
        billingAgent.on(json(post("/api/claims/admin/services/" + billingServiceId + "/agents"),
                        "{\"agentId\":" + billingAgent.id() + ",\"agentName\":\"Moi\"}"))
                .andExpect(status().isForbidden());
        admin.on(json(post("/api/claims/admin/services/" + billingServiceId + "/agents"),
                        "{\"agentId\":" + billingAgent.id() + ",\"agentName\":\"Doublon\"}"))
                .andExpect(status().isConflict());
    }

    // --- Pièces jointes --------------------------------------------------------------------------

    @Test
    void uploadAndDownloadFollowClaimVisibility() throws Exception {
        long id = createClaim(client);
        String body = client.on(upload(id, file("../../facture mars.pdf", PDF), file("photo.png", PNG)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].fileName").value("facture mars.pdf"))
                .andExpect(jsonPath("$[0].contentType").value("application/pdf"))
                .andExpect(jsonPath("$[1].contentType").value("image/png"))
                .andReturn().getResponse().getContentAsString();
        long attachmentId = ((Number) JsonPath.read(body, "$[0].id")).longValue();

        client.on(get("/api/claims/" + id + "/attachments/" + attachmentId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(PDF));
        admin.on(get("/api/claims/" + id + "/attachments/" + attachmentId)).andExpect(status().isOk());

        otherClient.on(get("/api/claims/" + id + "/attachments/" + attachmentId)).andExpect(status().isNotFound());
        billingAgent.on(get("/api/claims/" + id + "/attachments/" + attachmentId)).andExpect(status().isNotFound());
        otherClient.on(upload(id, file("x.pdf", PDF))).andExpect(status().isNotFound());

        client.on(get("/api/claims/" + id))
                .andExpect(jsonPath("$.attachments", hasSize(2)))
                .andExpect(jsonPath("$.attachments[0].fileName").value("facture mars.pdf"));
    }

    @Test
    void uploadRejectsDisallowedOrDisguisedFiles() throws Exception {
        long id = createClaim(client);
        client.on(upload(id, file("virus.exe", "MZ\u0090\u0000".getBytes(StandardCharsets.ISO_8859_1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Type de fichier non autorisé")));
        // Contenu PDF déguisé en image : refusé.
        client.on(upload(id, file("image.png", PDF)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("ne correspond pas")));
        // Faux PDF (texte avec extension .pdf) : refusé.
        client.on(upload(id, file("faux.pdf", "bonjour".getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isBadRequest());
        // Trop volumineux (5 Mo + 1 octet).
        byte[] big = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy(PDF, 0, big, 0, PDF.length);
        client.on(upload(id, file("gros.pdf", big)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("trop volumineux")));
        // Aucun fichier n'a été enregistré.
        client.on(get("/api/claims/" + id)).andExpect(jsonPath("$.attachments", hasSize(0)));
    }

    @Test
    void atMostFiveAttachmentsPerClaim() throws Exception {
        long id = createClaim(client);
        client.on(upload(id, file("1.pdf", PDF), file("2.pdf", PDF), file("3.pdf", PDF), file("4.pdf", PDF)))
                .andExpect(status().isCreated());
        client.on(upload(id, file("5.pdf", PDF), file("6.pdf", PDF))).andExpect(status().isConflict());
        client.on(upload(id, file("5.pdf", PDF))).andExpect(status().isCreated());
        client.on(upload(id, file("6.pdf", PDF))).andExpect(status().isConflict());
    }

    @Test
    void noUploadOnClosedOrRejectedClaim() throws Exception {
        long id = createClaim(client);
        admin.on(json(patch("/api/claims/" + id + "/status"), "{\"status\":\"REJECTED\"}")).andExpect(status().isOk());
        client.on(upload(id, file("x.pdf", PDF))).andExpect(status().isConflict());
    }

    // --- Outils de test --------------------------------------------------------------------------

    /** Appelant simulé : en-têtes que la gateway pose après vérification du JWT. */
    private record Caller(long id, UserRole role, MockMvc mvc) {

        ResultActions on(MockHttpServletRequestBuilder request) throws Exception {
            return mvc.perform(request
                    .header("X-User-Id", String.valueOf(id))
                    .header("X-User-Email", role.name().toLowerCase() + id + "@test.local")
                    .header("X-User-Role", role.name()));
        }
    }

    private Caller caller(UserRole role) {
        return new Caller(IDS.incrementAndGet(), role, mvc);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static MockMultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("files", name, MediaType.APPLICATION_OCTET_STREAM_VALUE, content);
    }

    private static MockMultipartHttpServletRequestBuilder upload(long claimId, MockMultipartFile... files) {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/claims/" + claimId + "/attachments");
        for (MockMultipartFile f : files) {
            request.file(f);
        }
        return request;
    }

    private long createClaim(Caller owner) throws Exception {
        String body = owner.on(json(post("/api/claims"),
                        "{\"title\":\"Facture erronée\",\"description\":\"Montant facturé deux fois\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private long classifiedClaim(long categoryId) throws Exception {
        long id = createClaim(client);
        admin.on(json(put("/api/claims/" + id + "/classification"), "{\"categoryId\":" + categoryId + "}"))
                .andExpect(status().isOk());
        return id;
    }

    private long assignedClaim() throws Exception {
        long id = classifiedClaim(billingCategoryId);
        admin.on(json(put("/api/claims/" + id + "/assignment"),
                        "{\"serviceId\":" + billingServiceId + ",\"agentId\":" + billingAgent.id() + "}"))
                .andExpect(status().isOk());
        return id;
    }

    private long resolvedClaim() throws Exception {
        long id = assignedClaim();
        billingAgent.on(json(patch("/api/claims/" + id + "/status"), "{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk());
        billingAgent.on(json(patch("/api/claims/" + id + "/status"), "{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isOk());
        return id;
    }

    private void addAgent(long serviceId, Caller agent, String name) throws Exception {
        admin.on(json(post("/api/claims/admin/services/" + serviceId + "/agents"),
                        "{\"agentId\":" + agent.id() + ",\"agentName\":\"" + name + "\"}"))
                .andExpect(status().isCreated());
    }

    private static long idByName(ResultActions list, String name) throws Exception {
        String body = list.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Number> ids = JsonPath.read(body, "$[?(@.name == '" + name + "')].id");
        return ids.get(0).longValue();
    }
}

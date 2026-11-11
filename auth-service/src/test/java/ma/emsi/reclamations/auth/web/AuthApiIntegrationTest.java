package ma.emsi.reclamations.auth.web;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Tests d'intégration des endpoints : application complète (sécurité, validation, JPA, Flyway)
 * sur H2 en mode PostgreSQL. L'admin initial (admin@test.local) est créé au démarrage par
 * AdminAccountInitializer à partir de src/test/resources/application.yml.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthApiIntegrationTest {

    private static final String ADMIN_EMAIL = "admin@test.local";
    private static final String ADMIN_PASSWORD = "Admin-Test-123";
    private static final AtomicInteger COUNTER = new AtomicInteger();

    @Autowired
    private MockMvc mvc;

    // --- Inscription (BF1) ----------------------------------------------------------------------

    @Test
    void register_createsClientAndNeverReturnsPassword() throws Exception {
        String email = uniqueEmail();
        register("Alice", email, "motdepasse123")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("CLIENT"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void register_ignoresRoleSentByCaller() throws Exception {
        String body = """
                {"name":"Pirate","email":"%s","password":"motdepasse123","role":"ADMIN"}
                """.formatted(uniqueEmail());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("CLIENT"));
    }

    @Test
    void register_invalidFieldsGive400WithFieldErrors() throws Exception {
        register("", "pas-un-email", "court")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void register_duplicateEmailGives409() throws Exception {
        String email = uniqueEmail();
        register("Alice", email, "motdepasse123").andExpect(status().isCreated());

        register("Alice bis", email.toUpperCase(), "motdepasse123")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Cet email est déjà utilisé"));
    }

    // --- Connexion (BF2) ------------------------------------------------------------------------

    @Test
    void login_returnsBearerToken() throws Exception {
        String email = uniqueEmail();
        register("Bob", email, "motdepasse123");

        login(email, "motdepasse123")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.email").value(email));
    }

    @Test
    void login_sameMessageForUnknownEmailAndWrongPassword() throws Exception {
        String email = uniqueEmail();
        register("Bob", email, "motdepasse123");

        login(email, "mauvais-mdp")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Email ou mot de passe incorrect"));
        login("inconnu-" + email, "motdepasse123")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Email ou mot de passe incorrect"));
    }

    // --- Profil (BF4) ---------------------------------------------------------------------------

    @Test
    void me_withoutTokenGives401Json() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Authentification requise"));
    }

    @Test
    void me_withInvalidTokenGives401() throws Exception {
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer abc.def.ghi"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Jeton invalide ou expiré"));
    }

    @Test
    void me_returnsProfileAndCanBeUpdated() throws Exception {
        String email = uniqueEmail();
        register("Chloé", email, "motdepasse123");
        String token = tokenFor(email, "motdepasse123");

        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.name").value("Chloé"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        // Mauvais mot de passe actuel → 400, rien ne change.
        mvc.perform(put("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"faux\",\"newPassword\":\"nouveau-mdp-123\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(put("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Chloé M.\",\"currentPassword\":\"motdepasse123\",\"newPassword\":\"nouveau-mdp-123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Chloé M."));

        login(email, "motdepasse123").andExpect(status().isUnauthorized());
        login(email, "nouveau-mdp-123").andExpect(status().isOk());
    }

    // --- Administration (BF21) ------------------------------------------------------------------

    @Test
    void admin_endpointsForbiddenForClient() throws Exception {
        String email = uniqueEmail();
        register("Client", email, "motdepasse123");
        String clientToken = tokenFor(email, "motdepasse123");

        mvc.perform(get("/api/auth/admin/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + clientToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void admin_canListCreateChangeRoleAndDisable() throws Exception {
        String adminToken = tokenFor(ADMIN_EMAIL, ADMIN_PASSWORD);

        mvc.perform(get("/api/auth/admin/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].email", hasItem(ADMIN_EMAIL)))
                .andExpect(jsonPath("$[*].passwordHash").doesNotExist());

        // Création d'un agent.
        String agentEmail = uniqueEmail();
        String created = mvc.perform(post("/api/auth/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Agent","email":"%s","password":"motdepasse123","role":"AGENT"}
                                """.formatted(agentEmail)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("AGENT"))
                .andReturn().getResponse().getContentAsString();
        Integer agentId = JsonPath.read(created, "$.id");

        // Changement de rôle.
        mvc.perform(patch("/api/auth/admin/users/{id}/role", agentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        // Désactivation → login refusé (403), jeton déjà émis inutilisable pour /me.
        String agentToken = tokenFor(agentEmail, "motdepasse123");
        mvc.perform(patch("/api/auth/admin/users/{id}/status", agentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        login(agentEmail, "motdepasse123")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Ce compte est désactivé"));
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + agentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_cannotCreateClientOrUseInvalidRole() throws Exception {
        String adminToken = tokenFor(ADMIN_EMAIL, ADMIN_PASSWORD);

        mvc.perform(post("/api/auth/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"X","email":"%s","password":"motdepasse123","role":"CLIENT"}
                                """.formatted(uniqueEmail())))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/auth/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"X","email":"%s","password":"motdepasse123","role":"SUPERUSER"}
                                """.formatted(uniqueEmail())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    // --- Utilitaires ----------------------------------------------------------------------------

    private static String uniqueEmail() {
        return "user" + COUNTER.incrementAndGet() + "-" + System.nanoTime() + "@test.local";
    }

    private ResultActions register(String name, String email, String password) throws Exception {
        String body = """
                {"name":"%s","email":"%s","password":"%s"}
                """.formatted(name, email, password);
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions login(String email, String password) throws Exception {
        String body = """
                {"email":"%s","password":"%s"}
                """.formatted(email, password);
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String tokenFor(String email, String password) throws Exception {
        String json = login(email, password).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.accessToken");
    }
}

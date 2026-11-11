package ma.emsi.reclamations.configserver;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Démarre le Config Server sur un port aléatoire et vérifie qu'il sert bien
 * la configuration d'exemple du dossier ../config-repo (Maven exécute les
 * tests depuis le dossier du module).
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class ConfigServerApplicationTests {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void contextLoads() {
        // Le contexte Spring démarre : rien d'autre à vérifier ici.
    }

    @Test
    void servesSampleConfigurationFromConfigRepo() {
        ResponseEntity<String> response = rest.getForEntity("/sample-service/default", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("sample-service.yml")
                .contains("Bonjour depuis le Config Server");
    }

    @Test
    void healthEndpointIsUp() {
        ResponseEntity<String> response = rest.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }
}

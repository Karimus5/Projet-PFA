package ma.emsi.reclamations.claim.ai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration de l'appel au service IA (config-repo/claim-service.yml, préfixe ai-classifier).
 *
 * @param enabled        false : aucun appel, toutes les réclamations restent NEW (classement manuel)
 * @param url            URL de base du service IA (pas de découverte Eureka, décision étape 7a)
 * @param apiKey         clé partagée (AI_SERVICE_API_KEY) envoyée dans l'en-tête X-API-Key ;
 *                       vide → appel désactivé (avec un avertissement au démarrage)
 * @param connectTimeout délai d'établissement de la connexion
 * @param readTimeout    délai de lecture de la réponse ; connectTimeout + readTimeout ≤ 2 s
 */
@ConfigurationProperties("ai-classifier")
public record AiClassifierProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("http://localhost:8000") String url,
        String apiKey,
        @DefaultValue("500ms") Duration connectTimeout,
        @DefaultValue("1500ms") Duration readTimeout) {

    /** Nom de l'instance Resilience4j (resilience4j.circuitbreaker.instances.ai-classifier). */
    public static final String CIRCUIT_BREAKER = "ai-classifier";

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}

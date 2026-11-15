package ma.emsi.reclamations.claim.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Client OpenFeign du service IA (ai-classifier-service, FastAPI). URL fixe lue dans la
 * configuration (pas d'Eureka) ; délais, clé API et absence de nouvelle tentative :
 * {@link AiClassifierFeignConfig}. Toujours appelé via {@link AiClassificationGateway}
 * (disjoncteur + repli), jamais directement.
 */
@FeignClient(name = "ai-classifier", url = "${ai-classifier.url:http://localhost:8000}",
        configuration = AiClassifierFeignConfig.class)
public interface AiClassifierClient {

    @PostMapping(path = "/classify", consumes = "application/json", produces = "application/json")
    ClassifyResponse classify(@RequestBody ClassifyRequest request);

    record ClassifyRequest(String title, String description) {
    }

    /** Réponse de POST /classify (champs inutiles ici ignorés : probabilities, reason...). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ClassifyResponse(
            String category,
            String service,
            Double confidence,
            Double threshold,
            Boolean thresholdReached,
            String modelVersion,
            Double processingTimeMs) {
    }
}

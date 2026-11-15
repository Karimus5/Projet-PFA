package ma.emsi.reclamations.claim.ai;

import feign.FeignException;
import feign.RetryableException;
import feign.codec.DecodeException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.net.SocketTimeoutException;
import ma.emsi.reclamations.claim.ai.AiClassifierClient.ClassifyRequest;
import ma.emsi.reclamations.claim.ai.AiClassifierClient.ClassifyResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Point d'appel UNIQUE du service IA : client Feign (délais stricts, pas de nouvelle tentative)
 * derrière un disjoncteur Resilience4j. Ne lève jamais d'exception : toute panne devient un
 * {@link AiCallResult.Failed} (repli vers le classement manuel, BF19).
 *
 * <p>Disjoncteur (config-repo/claim-service.yml, resilience4j.circuitbreaker.instances.ai-classifier) :
 * après trop d'échecs, il s'ouvre et les appels suivants sont refusés immédiatement (CIRCUIT_OPEN)
 * pendant la durée configurée, puis un appel d'essai est autorisé. Une réponse avec une confiance
 * basse n'est PAS un échec : seules les erreurs techniques comptent.
 *
 * <p>Le texte des réclamations n'est jamais journalisé.
 */
@Component
public class AiClassificationGateway {

    private static final Logger log = LoggerFactory.getLogger(AiClassificationGateway.class);

    private final AiClassifierClient client;
    private final AiClassifierProperties properties;
    private final CircuitBreaker circuitBreaker;

    public AiClassificationGateway(AiClassifierClient client, AiClassifierProperties properties,
                                   CircuitBreakerRegistry circuitBreakers) {
        this.client = client;
        this.properties = properties;
        this.circuitBreaker = circuitBreakers.circuitBreaker(AiClassifierProperties.CIRCUIT_BREAKER);
        if (!properties.enabled()) {
            log.info("Classement automatique désactivé (ai-classifier.enabled=false) : classement manuel uniquement");
        } else if (!properties.hasApiKey()) {
            log.warn("AI_SERVICE_API_KEY absente : classement automatique désactivé, classement manuel uniquement");
        } else {
            log.info("Service IA : {} (connexion {} ms, lecture {} ms)", properties.url(),
                    properties.connectTimeout().toMillis(), properties.readTimeout().toMillis());
        }
    }

    public boolean isEnabled() {
        return properties.enabled() && properties.hasApiKey();
    }

    public AiCallResult classify(String title, String description) {
        if (!isEnabled()) {
            return new AiCallResult.Disabled();
        }
        long start = System.nanoTime();
        try {
            ClassifyResponse response = circuitBreaker.executeSupplier(
                    () -> validate(client.classify(new ClassifyRequest(title, description))));
            log.debug("Service IA : réponse en {} ms", (System.nanoTime() - start) / 1_000_000);
            return new AiCallResult.Answered(response);
        } catch (RuntimeException e) {
            AiFailureReason reason = reasonOf(e);
            log.warn("Service IA indisponible ({}) après {} ms : classement manuel", reason,
                    (System.nanoTime() - start) / 1_000_000);
            return new AiCallResult.Failed(reason);
        }
    }

    /** État du disjoncteur (CLOSED, OPEN, HALF_OPEN...), pour les journaux et les tests. */
    public CircuitBreaker.State circuitState() {
        return circuitBreaker.getState();
    }

    private static ClassifyResponse validate(ClassifyResponse response) {
        if (response == null || response.confidence() == null || response.thresholdReached() == null
                || response.confidence() < 0 || response.confidence() > 1) {
            throw new InvalidAiResponseException();
        }
        return response;
    }

    static AiFailureReason reasonOf(Throwable e) {
        if (e instanceof CallNotPermittedException) {
            return AiFailureReason.CIRCUIT_OPEN;
        }
        if (e instanceof InvalidAiResponseException || e instanceof DecodeException) {
            return AiFailureReason.INVALID_RESPONSE;
        }
        if (e instanceof RetryableException) { // erreur réseau : connexion refusée, délai dépassé...
            for (Throwable cause = e; cause != null; cause = cause.getCause()) {
                if (cause instanceof SocketTimeoutException) {
                    return AiFailureReason.TIMEOUT;
                }
            }
            return AiFailureReason.UNREACHABLE;
        }
        if (e instanceof FeignException feign && feign.status() >= 400 && feign.status() < 500) {
            return AiFailureReason.REJECTED; // ex. clé API refusée (401) : erreur de configuration
        }
        return AiFailureReason.SERVER_ERROR;
    }

    /** Réponse 200 mais incomplète ou incohérente : traitée comme une panne. */
    static final class InvalidAiResponseException extends RuntimeException {
        InvalidAiResponseException() {
            super("Réponse du service IA invalide", null, false, false);
        }
    }
}

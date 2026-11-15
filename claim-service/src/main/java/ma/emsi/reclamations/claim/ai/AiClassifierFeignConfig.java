package ma.emsi.reclamations.claim.ai;

import feign.Request;
import feign.RequestInterceptor;
import feign.Retryer;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;

/**
 * Configuration PROPRE au client {@link AiClassifierClient} (volontairement sans @Configuration :
 * elle ne doit pas s'appliquer à d'autres clients Feign).
 * <ul>
 *   <li>délais stricts de connexion et de lecture (2 s au total par défaut) ;</li>
 *   <li>aucune nouvelle tentative : le repli vers le classement manuel est immédiat ;</li>
 *   <li>clé partagée dans l'en-tête X-API-Key (jamais journalisée).</li>
 * </ul>
 */
public class AiClassifierFeignConfig {

    static final String API_KEY_HEADER = "X-API-Key";

    @Bean
    Request.Options aiClassifierRequestOptions(AiClassifierProperties properties) {
        return new Request.Options(
                properties.connectTimeout().toMillis(), TimeUnit.MILLISECONDS,
                properties.readTimeout().toMillis(), TimeUnit.MILLISECONDS,
                false);
    }

    @Bean
    Retryer aiClassifierRetryer() {
        return Retryer.NEVER_RETRY;
    }

    @Bean
    RequestInterceptor aiClassifierApiKey(AiClassifierProperties properties) {
        return template -> {
            if (properties.hasApiKey()) {
                template.header(API_KEY_HEADER, properties.apiKey());
            }
        };
    }
}

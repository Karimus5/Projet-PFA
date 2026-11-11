package ma.emsi.reclamations.claim.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Implémentation par défaut : journalise l'événement, sans broker. Pour brancher RabbitMQ,
 * ajouter une implémentation annotée @Primary (ou supprimer celle-ci) : rien d'autre à modifier.
 */
@Component
public class LoggingClaimEventPublisher implements ClaimEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingClaimEventPublisher.class);

    @Override
    public void publish(ClaimEvent event) {
        log.info("Événement {} : réclamation {} ({} -> {}), service {}, agent {}, par {} {}",
                event.type(), event.claimId(), event.previousStatus(), event.status(),
                event.serviceId(), event.assignedAgentId(), event.actorRole(), event.actorId());
    }
}

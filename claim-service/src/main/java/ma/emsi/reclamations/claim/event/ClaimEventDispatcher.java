package ma.emsi.reclamations.claim.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Diffère la publication jusqu'au commit de la transaction en cours : si l'opération échoue
 * (409, rollback...), aucun événement n'est émis. Une erreur du publieur n'annule jamais
 * l'opération métier (elle est seulement journalisée).
 */
@Component
public class ClaimEventDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ClaimEventDispatcher.class);

    private final ClaimEventPublisher publisher;

    public ClaimEventDispatcher(ClaimEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void dispatchAfterCommit(ClaimEvent event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    safePublish(event);
                }
            });
        } else {
            safePublish(event);
        }
    }

    private void safePublish(ClaimEvent event) {
        try {
            publisher.publish(event);
        } catch (RuntimeException e) {
            log.error("Échec de publication de l'événement {} (réclamation {})", event.type(), event.claimId(), e);
        }
    }
}

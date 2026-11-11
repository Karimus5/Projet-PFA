package ma.emsi.reclamations.claim.event;

/**
 * Point d'extension pour diffuser les événements des réclamations.
 *
 * <p>Aujourd'hui : {@link LoggingClaimEventPublisher} (journalisation seule, aucun broker).
 * Plus tard : une implémentation RabbitMQ (notification-service, stats-service) remplacera
 * celle-ci (en la déclarant @Primary), SANS modifier ClaimService.
 *
 * <p>Appelé uniquement après le commit de la transaction ({@link ClaimEventDispatcher}) :
 * une implémentation n'a donc jamais à gérer un événement d'une transaction annulée.
 */
public interface ClaimEventPublisher {

    void publish(ClaimEvent event);
}

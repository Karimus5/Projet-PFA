package ma.emsi.reclamations.claim.event;

/** Types d'événements métier destinés aux futurs notification-service (BF10) et stats-service (BF24-25). */
public enum ClaimEventType {
    /** Réclamation soumise par un client. */
    CREATED,
    /** Changement de statut (y compris classification NEW → CLASSIFIED et affectation → ASSIGNED). */
    STATUS_CHANGED,
    /** Catégorie ou service corrigé après classification (base du taux de correction, BF25). */
    CLASSIFICATION_CHANGED,
    /** Affectation ou réaffectation à un service / un agent. */
    ASSIGNED,
    /** Commentaire PUBLIC ajouté (les commentaires internes ne génèrent aucun événement). */
    PUBLIC_COMMENT_ADDED
}

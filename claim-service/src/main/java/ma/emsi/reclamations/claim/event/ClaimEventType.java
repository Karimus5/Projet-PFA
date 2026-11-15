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
    PUBLIC_COMMENT_ADDED,
    /**
     * Classement automatique appliqué par le service IA (NEW → CLASSIFIED, acteur SYSTEM). detail =
     * confiance et version du modèle. Accompagné d'un STATUS_CHANGED.
     */
    AUTO_CLASSIFIED,
    /**
     * Retour d'un agent / admin sur une classification IA (BF14, BF20) : detail = CONFIRMED ou
     * CORRECTED (base du taux de correction des prédictions, BF25).
     */
    CLASSIFICATION_FEEDBACK
}

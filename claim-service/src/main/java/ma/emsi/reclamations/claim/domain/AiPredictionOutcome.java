package ma.emsi.reclamations.claim.domain;

/** Résultat de l'appel au service IA lors de la création d'une réclamation. */
public enum AiPredictionOutcome {
    /** Confiance ≥ seuil et catégorie connue : réclamation classée automatiquement (NEW → CLASSIFIED). */
    APPLIED,
    /** Confiance sous le seuil (ou texte inexploitable) : classement manuel. */
    LOW_CONFIDENCE,
    /** Catégorie proposée absente du référentiel du claim-service : classement manuel. */
    UNKNOWN_CATEGORY,
    /** Erreur, délai dépassé, service arrêté ou disjoncteur ouvert : classement manuel. */
    UNAVAILABLE;

    public boolean sentToManual() {
        return this != APPLIED;
    }
}

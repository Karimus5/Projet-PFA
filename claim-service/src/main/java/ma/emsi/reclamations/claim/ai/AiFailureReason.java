package ma.emsi.reclamations.claim.ai;

/** Pourquoi le service IA n'a pas pu répondre (trace interne, jamais montrée au client). */
public enum AiFailureReason {
    TIMEOUT("délai de réponse dépassé"),
    UNREACHABLE("service injoignable ou arrêté"),
    SERVER_ERROR("erreur du service IA"),
    REJECTED("appel refusé par le service IA"),
    INVALID_RESPONSE("réponse du service IA invalide"),
    CIRCUIT_OPEN("service IA désactivé temporairement après plusieurs échecs");

    private final String label;

    AiFailureReason(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}

package ma.emsi.reclamations.claim.domain;

/** Origine de la catégorie / du service d'une réclamation : MANUAL (agent / admin) ou AI (ai-classifier-service). */
public enum ClassificationSource {
    MANUAL,
    AI
}

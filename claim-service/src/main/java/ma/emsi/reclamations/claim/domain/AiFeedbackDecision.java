package ma.emsi.reclamations.claim.domain;

/** Retour d'un agent ou d'un admin sur une classification automatique (BF14, BF20). */
public enum AiFeedbackDecision {
    /** La catégorie et le service proposés par l'IA sont validés. */
    CONFIRMED,
    /** La catégorie ou le service ont été changés. */
    CORRECTED
}

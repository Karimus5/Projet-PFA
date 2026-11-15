package ma.emsi.reclamations.claim.ai;

import ma.emsi.reclamations.claim.ai.AiClassifierClient.ClassifyResponse;

/** Résultat d'un appel au service IA, sans jamais d'exception pour l'appelant. */
public sealed interface AiCallResult {

    /** Appel désactivé par la configuration (ai-classifier.enabled=false ou clé absente). */
    record Disabled() implements AiCallResult {
    }

    /** Réponse valide reçue (la confiance peut être sous le seuil). */
    record Answered(ClassifyResponse response) implements AiCallResult {
    }

    /** Pas de réponse exploitable : repli vers le classement manuel. */
    record Failed(AiFailureReason reason) implements AiCallResult {
    }
}

package ma.emsi.reclamations.claim.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Cycle de vie d'une réclamation (diagramme d'états du cahier des charges, §8.4).
 *
 * <pre>
 * NEW ──classification──▶ CLASSIFIED ──affectation──▶ ASSIGNED ──prise en charge──▶ IN_PROGRESS
 *  │                                                                       ▲   │ résolution
 *  └─demande invalide─▶ REJECTED                                  rouverte │   ▼
 *                                                      CLOSED ◀─validée── RESOLVED
 * </pre>
 *
 * Toute autre transition est interdite (409). Qui a le droit de déclencher chaque transition :
 * voir {@link ClaimWorkflow}.
 */
public enum ClaimStatus {
    NEW,
    CLASSIFIED,
    ASSIGNED,
    IN_PROGRESS,
    RESOLVED,
    CLOSED,
    REJECTED;

    private static final Map<ClaimStatus, Set<ClaimStatus>> TRANSITIONS = new EnumMap<>(ClaimStatus.class);

    static {
        TRANSITIONS.put(NEW, EnumSet.of(CLASSIFIED, REJECTED));
        TRANSITIONS.put(CLASSIFIED, EnumSet.of(ASSIGNED));
        TRANSITIONS.put(ASSIGNED, EnumSet.of(IN_PROGRESS));
        TRANSITIONS.put(IN_PROGRESS, EnumSet.of(RESOLVED));
        TRANSITIONS.put(RESOLVED, EnumSet.of(CLOSED, IN_PROGRESS));
        TRANSITIONS.put(CLOSED, EnumSet.noneOf(ClaimStatus.class));
        TRANSITIONS.put(REJECTED, EnumSet.noneOf(ClaimStatus.class));
    }

    public boolean canTransitionTo(ClaimStatus target) {
        return TRANSITIONS.get(this).contains(target);
    }

    /** Réclamation « ouverte » : ni clôturée ni rejetée (compléments, commentaires, pièces jointes). */
    public boolean isOpen() {
        return this != CLOSED && this != REJECTED;
    }
}

package ma.emsi.reclamations.claim.service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import ma.emsi.reclamations.claim.domain.AiFeedbackDecision;
import ma.emsi.reclamations.claim.domain.AiPrediction;
import ma.emsi.reclamations.claim.domain.AiPredictionOutcome;
import ma.emsi.reclamations.claim.domain.AiPredictionRepository;
import ma.emsi.reclamations.claim.error.ApiException;
import ma.emsi.reclamations.claim.security.CurrentUser;
import ma.emsi.reclamations.claim.web.dto.Dtos.AiStatsResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Administration du classement automatique (ADMIN uniquement, contrôlé ici ET à la gateway) :
 * chiffres pour le tableau de bord (BF25) et export CSV des retours d'information pour
 * réentraîner le modèle (BF20).
 */
@Service
@Transactional(readOnly = true)
public class AiFeedbackService {

    /**
     * Colonnes de l'export. Aucune donnée personnelle inutile : ni e-mail ni identifiant du client,
     * ni identifiant de l'agent (seulement son rôle). Le texte de la réclamation est inclus car il
     * sert à réentraîner le modèle.
     */
    static final List<String> CSV_COLUMNS = List.of("claim_id", "title", "description",
            "predicted_category", "predicted_service", "confidence", "final_category", "final_service",
            "decision", "model_version", "decided_by_role", "decided_at");

    private final AiPredictionRepository predictions;

    public AiFeedbackService(AiPredictionRepository predictions) {
        this.predictions = predictions;
    }

    public AiStatsResponse stats(CurrentUser user) {
        requireAdmin(user);
        long total = predictions.count();
        long applied = predictions.countByOutcome(AiPredictionOutcome.APPLIED);
        long confirmed = predictions.countByDecision(AiFeedbackDecision.CONFIRMED);
        long corrected = predictions.countByDecision(AiFeedbackDecision.CORRECTED);
        long low = predictions.countByOutcome(AiPredictionOutcome.LOW_CONFIDENCE);
        long unknown = predictions.countByOutcome(AiPredictionOutcome.UNKNOWN_CATEGORY);
        long unavailable = predictions.countByOutcome(AiPredictionOutcome.UNAVAILABLE);
        return new AiStatsResponse(total, applied, confirmed, corrected,
                predictions.countByOutcomeAndDecisionIsNull(AiPredictionOutcome.APPLIED),
                low + unknown + unavailable, low, unknown, unavailable,
                ratio(applied, total), ratio(corrected, confirmed + corrected));
    }

    /** CSV RFC 4180 (UTF-8, séparateur virgule, tous les champs entre guillemets). */
    public String exportCsv(CurrentUser user) {
        requireAdmin(user);
        StringBuilder csv = new StringBuilder();
        csv.append(String.join(",", CSV_COLUMNS)).append("\r\n");
        for (AiPrediction p : predictions.findAllWithDecision()) {
            csv.append(String.join(",", List.of(
                    field(String.valueOf(p.getClaim().getId())),
                    field(p.getClaim().getTitle()),
                    field(p.getClaim().getDescription()),
                    field(p.getPredictedCategory()),
                    field(p.getPredictedService()),
                    field(p.getConfidence() == null ? "" : String.format(Locale.ROOT, "%.4f", p.getConfidence())),
                    field(p.getFinalCategory()),
                    field(p.getFinalService()),
                    field(p.getDecision().name()),
                    field(p.getModelVersion()),
                    field(p.getDecidedByRole() == null ? "" : p.getDecidedByRole().name()),
                    field(p.getDecidedAt() == null ? "" : DateTimeFormatter.ISO_INSTANT.format(p.getDecidedAt())))))
                    .append("\r\n");
        }
        return csv.toString();
    }

    /**
     * Champ CSV entre guillemets (guillemets internes doublés). Un texte qui commence par = + - @
     * (ou tabulation / retour chariot) est préfixé d'une apostrophe : sinon un tableur pourrait
     * l'exécuter comme une formule (injection CSV, le texte vient des clients).
     */
    static String field(String value) {
        if (value == null) {
            return "\"\"";
        }
        String safe = !value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private static Double ratio(long part, long total) {
        return total == 0 ? null : (double) part / total;
    }

    private static void requireAdmin(CurrentUser user) {
        if (!user.isAdmin()) {
            throw ApiException.forbidden("Réservé aux administrateurs");
        }
    }
}

package ma.emsi.reclamations.claim.service;

import java.util.List;
import java.util.regex.Pattern;
import ma.emsi.reclamations.claim.domain.AiPrediction;
import ma.emsi.reclamations.claim.domain.Attachment;
import ma.emsi.reclamations.claim.domain.ClassificationSource;
import ma.emsi.reclamations.claim.security.UserRole;
import ma.emsi.reclamations.claim.domain.Category;
import ma.emsi.reclamations.claim.domain.Claim;
import ma.emsi.reclamations.claim.domain.Comment;
import ma.emsi.reclamations.claim.domain.ProcessingService;
import ma.emsi.reclamations.claim.domain.ServiceAgent;
import ma.emsi.reclamations.claim.domain.StatusHistory;
import ma.emsi.reclamations.claim.web.dto.Dtos.AgentRef;
import ma.emsi.reclamations.claim.web.dto.Dtos.AiPredictionResponse;
import ma.emsi.reclamations.claim.web.dto.Dtos.AttachmentResponse;
import ma.emsi.reclamations.claim.web.dto.Dtos.CategoryResponse;
import ma.emsi.reclamations.claim.web.dto.Dtos.ClaimDetail;
import ma.emsi.reclamations.claim.web.dto.Dtos.ClaimSummary;
import ma.emsi.reclamations.claim.web.dto.Dtos.CommentResponse;
import ma.emsi.reclamations.claim.web.dto.Dtos.RefResponse;
import ma.emsi.reclamations.claim.web.dto.Dtos.ServiceAgentResponse;
import ma.emsi.reclamations.claim.web.dto.Dtos.ServiceResponse;
import ma.emsi.reclamations.claim.web.dto.Dtos.StatusHistoryResponse;

/** Conversion entités → DTO (appelée dans une transaction : les relations LAZY sont accessibles). */
final class DtoMapper {

    private DtoMapper() {
    }

    /** Note d'un classement automatique : « … (92 %) » final (notes antérieures à l'étape 7c). */
    private static final Pattern CONFIDENCE_IN_NOTE = Pattern.compile("\\s*\\(\\d+ %\\)\\s*$");

    /**
     * @param staff false pour un CLIENT : ni source, ni confiance (seulement autoClassified)
     */
    static ClaimSummary summary(Claim c, boolean staff) {
        return new ClaimSummary(c.getId(), c.getTitle(), c.getStatus(), category(c), service(c), agent(c),
                staff ? c.getClassificationSource() : null, staff ? c.getConfidenceScore() : null,
                autoClassified(c), c.getCreatedAt(), c.getUpdatedAt());
    }

    static boolean autoClassified(Claim c) {
        return c.getClassificationSource() == ClassificationSource.AI;
    }

    /**
     * @param staff      false pour un CLIENT : aucune donnée du modèle (source, confiance, prédiction,
     *                   pourcentage dans les notes d'historique)
     * @param prediction prédiction IA (ignorée si staff = false)
     */
    static ClaimDetail detail(Claim c, List<StatusHistory> history, List<Comment> comments,
                              List<Attachment> attachments, AiPrediction prediction, boolean staff) {
        return new ClaimDetail(c.getId(), c.getTitle(), c.getDescription(), c.getStatus(),
                c.getClientId(), c.getClientEmail(), category(c), service(c), agent(c),
                staff ? c.getClassificationSource() : null, staff ? c.getConfidenceScore() : null,
                autoClassified(c), c.getCreatedAt(), c.getUpdatedAt(),
                history.stream().map(h -> history(h, staff)).toList(),
                comments.stream().map(DtoMapper::comment).toList(),
                attachments.stream().map(DtoMapper::attachment).toList(),
                staff && prediction != null ? prediction(prediction) : null);
    }

    static AiPredictionResponse prediction(AiPrediction p) {
        return new AiPredictionResponse(p.getOutcome(), p.getFailureReason(), p.getPredictedCategory(),
                p.getPredictedService(), p.getConfidence(), p.getModelVersion(), p.getThreshold(), p.getCreatedAt(),
                p.getDecision(), p.getFinalCategory(), p.getFinalService(), p.getDecidedByRole(),
                p.getDecidedByName(), p.getDecidedAt());
    }

    /**
     * Pour un client, le pourcentage de confiance est retiré des notes du classement automatique
     * (auteur SYSTEM) écrites avant l'étape 7c (« Classement automatique : Livraison (99 %) »).
     */
    static StatusHistoryResponse history(StatusHistory h, boolean staff) {
        String note = h.getNote();
        if (!staff && note != null && h.getAuthorRole() == UserRole.SYSTEM) {
            note = CONFIDENCE_IN_NOTE.matcher(note).replaceAll("");
        }
        return new StatusHistoryResponse(h.getOldStatus(), h.getNewStatus(), h.getAuthorId(),
                h.getAuthorRole(), note, h.getChangedAt());
    }

    static CommentResponse comment(Comment c) {
        return new CommentResponse(c.getId(), c.getContent(), c.isInternal(), c.getAuthorId(),
                c.getAuthorRole(), c.getCreatedAt());
    }

    static AttachmentResponse attachment(Attachment a) {
        return new AttachmentResponse(a.getId(), a.getFileName(), a.getContentType(), a.getSizeBytes(),
                a.getUploadedById(), a.getUploadedAt());
    }

    static ServiceResponse service(ProcessingService s) {
        return new ServiceResponse(s.getId(), s.getName(), s.getDescription());
    }

    static CategoryResponse category(Category c) {
        return new CategoryResponse(c.getId(), c.getName(), c.getDescription(),
                new RefResponse(c.getService().getId(), c.getService().getName()));
    }

    static ServiceAgentResponse agent(ServiceAgent sa) {
        return new ServiceAgentResponse(sa.getAgentId(), sa.getAgentName(), sa.getService().getId());
    }

    private static RefResponse category(Claim c) {
        return c.getCategory() == null ? null : new RefResponse(c.getCategory().getId(), c.getCategory().getName());
    }

    private static RefResponse service(Claim c) {
        return c.getService() == null ? null : new RefResponse(c.getService().getId(), c.getService().getName());
    }

    private static AgentRef agent(Claim c) {
        return c.getAssignedAgentId() == null ? null : new AgentRef(c.getAssignedAgentId(), c.getAssignedAgentName());
    }
}

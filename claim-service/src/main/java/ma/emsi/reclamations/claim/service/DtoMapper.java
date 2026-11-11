package ma.emsi.reclamations.claim.service;

import java.util.List;
import ma.emsi.reclamations.claim.domain.Attachment;
import ma.emsi.reclamations.claim.domain.Category;
import ma.emsi.reclamations.claim.domain.Claim;
import ma.emsi.reclamations.claim.domain.Comment;
import ma.emsi.reclamations.claim.domain.ProcessingService;
import ma.emsi.reclamations.claim.domain.ServiceAgent;
import ma.emsi.reclamations.claim.domain.StatusHistory;
import ma.emsi.reclamations.claim.web.dto.Dtos.AgentRef;
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

    static ClaimSummary summary(Claim c) {
        return new ClaimSummary(c.getId(), c.getTitle(), c.getStatus(), category(c), service(c), agent(c),
                c.getClassificationSource(), c.getConfidenceScore(), c.getCreatedAt(), c.getUpdatedAt());
    }

    static ClaimDetail detail(Claim c, List<StatusHistory> history, List<Comment> comments,
                              List<Attachment> attachments) {
        return new ClaimDetail(c.getId(), c.getTitle(), c.getDescription(), c.getStatus(),
                c.getClientId(), c.getClientEmail(), category(c), service(c), agent(c),
                c.getClassificationSource(), c.getConfidenceScore(), c.getCreatedAt(), c.getUpdatedAt(),
                history.stream().map(DtoMapper::history).toList(),
                comments.stream().map(DtoMapper::comment).toList(),
                attachments.stream().map(DtoMapper::attachment).toList());
    }

    static StatusHistoryResponse history(StatusHistory h) {
        return new StatusHistoryResponse(h.getOldStatus(), h.getNewStatus(), h.getAuthorId(),
                h.getAuthorRole(), h.getNote(), h.getChangedAt());
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

package ma.emsi.reclamations.claim.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import ma.emsi.reclamations.claim.domain.ClaimStatus;
import ma.emsi.reclamations.claim.domain.ClassificationSource;
import ma.emsi.reclamations.claim.security.UserRole;
import org.springframework.data.domain.Page;

/** Corps de requête et de réponse de l'API (jamais d'entité JPA exposée directement). */
public final class Dtos {

    private Dtos() {
    }

    // --- Requêtes : réclamations -----------------------------------------------------------------

    public record CreateClaimRequest(
            @NotBlank(message = "Le titre est obligatoire")
            @Size(max = 150, message = "Le titre ne doit pas dépasser 150 caractères")
            String title,
            @NotBlank(message = "La description est obligatoire")
            @Size(max = 5000, message = "La description ne doit pas dépasser 5000 caractères")
            String description) {
    }

    /** Complément d'information du client (BF9), ajouté à la fin de la description. */
    public record ComplementRequest(
            @NotBlank(message = "Le complément est obligatoire")
            @Size(max = 2000, message = "Le complément ne doit pas dépasser 2000 caractères")
            String text) {
    }

    /** internal absent = commentaire public. Un client ne peut pas écrire de commentaire interne. */
    public record CommentRequest(
            @NotBlank(message = "Le commentaire est obligatoire")
            @Size(max = 2000, message = "Le commentaire ne doit pas dépasser 2000 caractères")
            String content,
            Boolean internal) {

        public boolean isInternal() {
            return Boolean.TRUE.equals(internal);
        }
    }

    public record StatusChangeRequest(
            @NotNull(message = "Le statut est obligatoire")
            ClaimStatus status,
            @Size(max = 500, message = "La note ne doit pas dépasser 500 caractères")
            String note) {
    }

    /** serviceId absent = service qui traite la catégorie. */
    public record ClassificationRequest(
            @NotNull(message = "La catégorie est obligatoire")
            Long categoryId,
            Long serviceId) {
    }

    /** agentId absent = affectation au service seulement (file du service). */
    public record AssignmentRequest(
            @NotNull(message = "Le service est obligatoire")
            Long serviceId,
            Long agentId) {
    }

    // --- Requêtes : référentiels (admin) ---------------------------------------------------------

    public record ServiceRequest(
            @NotBlank(message = "Le nom est obligatoire")
            @Size(max = 100, message = "Le nom ne doit pas dépasser 100 caractères")
            String name,
            @Size(max = 255, message = "La description ne doit pas dépasser 255 caractères")
            String description) {
    }

    public record CategoryRequest(
            @NotBlank(message = "Le nom est obligatoire")
            @Size(max = 100, message = "Le nom ne doit pas dépasser 100 caractères")
            String name,
            @Size(max = 255, message = "La description ne doit pas dépasser 255 caractères")
            String description,
            @NotNull(message = "Le service est obligatoire")
            Long serviceId) {
    }

    /** agentId = id du compte AGENT dans auth-service ; agentName = nom affiché (saisi par l'admin). */
    public record ServiceAgentRequest(
            @NotNull(message = "L'identifiant de l'agent est obligatoire")
            @Positive(message = "L'identifiant de l'agent doit être positif")
            Long agentId,
            @NotBlank(message = "Le nom de l'agent est obligatoire")
            @Size(max = 100, message = "Le nom ne doit pas dépasser 100 caractères")
            String agentName) {
    }

    // --- Réponses --------------------------------------------------------------------------------

    public record RefResponse(Long id, String name) {
    }

    public record AgentRef(Long id, String name) {
    }

    public record ClaimSummary(
            Long id,
            String title,
            ClaimStatus status,
            RefResponse category,
            RefResponse service,
            AgentRef assignedAgent,
            ClassificationSource classificationSource,
            Double confidenceScore,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record CommentResponse(
            Long id,
            String content,
            boolean internal,
            Long authorId,
            UserRole authorRole,
            Instant createdAt) {
    }

    public record StatusHistoryResponse(
            ClaimStatus oldStatus,
            ClaimStatus newStatus,
            Long authorId,
            UserRole authorRole,
            String note,
            Instant changedAt) {
    }

    public record AttachmentResponse(
            Long id,
            String fileName,
            String contentType,
            long size,
            Long uploadedById,
            Instant uploadedAt) {
    }

    /** Détail (BF8). Pour un client, comments ne contient jamais de commentaire interne. */
    public record ClaimDetail(
            Long id,
            String title,
            String description,
            ClaimStatus status,
            Long clientId,
            String clientEmail,
            RefResponse category,
            RefResponse service,
            AgentRef assignedAgent,
            ClassificationSource classificationSource,
            Double confidenceScore,
            Instant createdAt,
            Instant updatedAt,
            List<StatusHistoryResponse> history,
            List<CommentResponse> comments,
            List<AttachmentResponse> attachments) {
    }

    public record ServiceResponse(Long id, String name, String description) {
    }

    public record CategoryResponse(Long id, String name, String description, RefResponse service) {
    }

    public record ServiceAgentResponse(Long agentId, String agentName, Long serviceId) {
    }

    /** Page stable et indépendante de Spring Data (format JSON documenté pour le frontend). */
    public record PageResponse<T>(
            List<T> content,
            int page,
            int size,
            long totalElements,
            int totalPages) {

        public static <T> PageResponse<T> of(Page<T> page) {
            return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                    page.getTotalElements(), page.getTotalPages());
        }
    }
}

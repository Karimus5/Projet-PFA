package ma.emsi.reclamations.claim.service;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import ma.emsi.reclamations.claim.access.ClaimAccess;
import ma.emsi.reclamations.claim.access.ClaimAccessPolicy;
import ma.emsi.reclamations.claim.domain.AttachmentRepository;
import ma.emsi.reclamations.claim.domain.Category;
import ma.emsi.reclamations.claim.domain.CategoryRepository;
import ma.emsi.reclamations.claim.domain.Claim;
import ma.emsi.reclamations.claim.domain.ClaimRepository;
import ma.emsi.reclamations.claim.domain.ClaimStatus;
import ma.emsi.reclamations.claim.domain.ClaimWorkflow;
import ma.emsi.reclamations.claim.domain.ClassificationSource;
import ma.emsi.reclamations.claim.domain.Comment;
import ma.emsi.reclamations.claim.domain.CommentRepository;
import ma.emsi.reclamations.claim.domain.ProcessingService;
import ma.emsi.reclamations.claim.domain.ProcessingServiceRepository;
import ma.emsi.reclamations.claim.domain.ServiceAgent;
import ma.emsi.reclamations.claim.domain.ServiceAgentRepository;
import ma.emsi.reclamations.claim.domain.StatusHistory;
import ma.emsi.reclamations.claim.domain.StatusHistoryRepository;
import ma.emsi.reclamations.claim.error.ApiException;
import ma.emsi.reclamations.claim.event.ClaimEvent;
import ma.emsi.reclamations.claim.event.ClaimEventDispatcher;
import ma.emsi.reclamations.claim.event.ClaimEventType;
import ma.emsi.reclamations.claim.security.CurrentUser;
import ma.emsi.reclamations.claim.web.dto.Dtos.AssignmentRequest;
import ma.emsi.reclamations.claim.web.dto.Dtos.ClaimDetail;
import ma.emsi.reclamations.claim.web.dto.Dtos.ClaimSummary;
import ma.emsi.reclamations.claim.web.dto.Dtos.ClassificationRequest;
import ma.emsi.reclamations.claim.web.dto.Dtos.CommentRequest;
import ma.emsi.reclamations.claim.web.dto.Dtos.CommentResponse;
import ma.emsi.reclamations.claim.web.dto.Dtos.CreateClaimRequest;
import ma.emsi.reclamations.claim.web.dto.Dtos.PageResponse;
import ma.emsi.reclamations.claim.web.dto.Dtos.StatusChangeRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cycle de vie des réclamations (BF6-BF9, BF11-BF15, BF23).
 *
 * <p>Ordre des contrôles pour toute action sur une réclamation :
 * <ol>
 *   <li>visibilité ({@link ClaimAccess#loadVisible}) → 404 si non visible ;</li>
 *   <li>action réservée à un autre rôle (ex. client qui affecte) → 403 ;</li>
 *   <li>état incompatible → 409. Pour un changement de statut ({@link ClaimWorkflow}) : transition
 *       absente du diagramme → 409, puis transition existante mais interdite au rôle → 403.</li>
 * </ol>
 * Chaque changement de statut est historisé (StatusHistory) et produit un événement après commit.
 */
@Service
@Transactional
public class ClaimService {

    private static final int MAX_DESCRIPTION_LENGTH = 10_000;
    private static final DateTimeFormatter COMPLEMENT_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm 'UTC'");

    private final ClaimRepository claims;
    private final CategoryRepository categories;
    private final ProcessingServiceRepository services;
    private final ServiceAgentRepository serviceAgents;
    private final CommentRepository comments;
    private final StatusHistoryRepository history;
    private final AttachmentRepository attachments;
    private final ClaimAccess access;
    private final ClaimEventDispatcher events;

    public ClaimService(ClaimRepository claims, CategoryRepository categories, ProcessingServiceRepository services,
                        ServiceAgentRepository serviceAgents, CommentRepository comments,
                        StatusHistoryRepository history, AttachmentRepository attachments,
                        ClaimAccess access, ClaimEventDispatcher events) {
        this.claims = claims;
        this.categories = categories;
        this.services = services;
        this.serviceAgents = serviceAgents;
        this.comments = comments;
        this.history = history;
        this.attachments = attachments;
        this.access = access;
        this.events = events;
    }

    // --- Client : soumission (BF6) et complément (BF9) -------------------------------------------

    public ClaimDetail create(CurrentUser user, CreateClaimRequest request) {
        if (!user.isClient()) {
            throw ApiException.forbidden("Seul un client peut soumettre une réclamation");
        }
        Claim claim = claims.save(new Claim(request.title().trim(), request.description().trim(),
                user.id(), user.email()));
        history.save(new StatusHistory(claim, null, ClaimStatus.NEW, user.id(), user.role(), "Réclamation soumise"));
        events.dispatchAfterCommit(ClaimEvent.of(ClaimEventType.CREATED, claim, null, user));
        return detail(claim, user);
    }

    public ClaimDetail addComplement(CurrentUser user, Long claimId, String text) {
        Claim claim = access.loadVisible(claimId, user);
        if (!user.isClient()) {
            throw ApiException.forbidden("Seul le client peut compléter sa réclamation");
        }
        requireOpen(claim);
        String complement = "\n\n--- Complément du "
                + COMPLEMENT_DATE.format(ZonedDateTime.now(ZoneOffset.UTC)) + " ---\n" + text.trim();
        if (claim.getDescription().length() + complement.length() > MAX_DESCRIPTION_LENGTH) {
            throw ApiException.badRequest("La description complétée dépasserait "
                    + MAX_DESCRIPTION_LENGTH + " caractères");
        }
        claim.appendToDescription(complement);
        claims.flush(); // met à jour updated_at (et vérifie le verrou optimiste) avant de répondre
        return detail(claim, user);
    }

    // --- Consultation (BF7, BF8, BF11) -----------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<ClaimSummary> list(CurrentUser user, ClaimStatus status, Pageable pageable) {
        return PageResponse.of(claims.findAll(access.visibleTo(user, status), pageable).map(DtoMapper::summary));
    }

    @Transactional(readOnly = true)
    public ClaimDetail get(CurrentUser user, Long claimId) {
        return detail(access.loadVisible(claimId, user), user);
    }

    // --- Commentaires (BF13) ---------------------------------------------------------------------

    public CommentResponse addComment(CurrentUser user, Long claimId, CommentRequest request) {
        Claim claim = access.loadVisible(claimId, user);
        if (request.isInternal() && !ClaimAccessPolicy.canSeeInternalComments(user)) {
            throw ApiException.forbidden("Un client ne peut pas écrire de commentaire interne");
        }
        requireOpen(claim);
        Comment comment = comments.save(new Comment(claim, user.id(), user.role(),
                request.content().trim(), request.isInternal()));
        if (!comment.isInternal()) {
            events.dispatchAfterCommit(ClaimEvent.of(ClaimEventType.PUBLIC_COMMENT_ADDED, claim, null, user));
        }
        return DtoMapper.comment(comment);
    }

    // --- Statut (BF12, validation / réouverture par le client) -----------------------------------

    public ClaimDetail changeStatus(CurrentUser user, Long claimId, StatusChangeRequest request) {
        Claim claim = access.loadVisible(claimId, user);
        ClaimStatus target = request.status();
        if (target == ClaimStatus.CLASSIFIED) {
            throw ApiException.badRequest("Pour classer une réclamation, utilisez PUT /api/claims/{id}/classification");
        }
        if (target == ClaimStatus.ASSIGNED) {
            throw ApiException.badRequest("Pour affecter une réclamation, utilisez PUT /api/claims/{id}/assignment");
        }
        transition(claim, target, user, blankToNull(request.note()));
        return detail(claim, user);
    }

    // --- Classification manuelle et correction (BF14, BF19 sans IA) ------------------------------

    /**
     * NEW → CLASSIFIED (tri manuel, ADMIN uniquement). Sur une réclamation déjà classée et non
     * résolue, corrige la catégorie / le service sans changer le statut (agent du service ou admin).
     */
    public ClaimDetail classify(CurrentUser user, Long claimId, ClassificationRequest request) {
        Claim claim = access.loadVisible(claimId, user);
        if (user.isClient()) {
            throw ApiException.forbidden("Un client ne peut pas classer une réclamation");
        }
        Category category = categories.findById(request.categoryId())
                .orElseThrow(() -> ApiException.badRequest("Catégorie inconnue : " + request.categoryId()));
        ProcessingService service = request.serviceId() == null
                ? category.getService()
                : findService(request.serviceId());

        ClaimStatus status = claim.getStatus();
        if (status == ClaimStatus.NEW) {
            ClaimWorkflow.requireTransition(status, ClaimStatus.CLASSIFIED, user.role());
            claim.classify(category, service, ClassificationSource.MANUAL, null);
            transition(claim, ClaimStatus.CLASSIFIED, user, "Classement manuel : " + category.getName());
        } else if (status == ClaimStatus.CLASSIFIED || status == ClaimStatus.ASSIGNED
                || status == ClaimStatus.IN_PROGRESS) {
            String trace = "Classification corrigée : " + nameOf(claim.getCategory()) + " → " + category.getName()
                    + ", service " + nameOf(claim.getService()) + " → " + service.getName();
            claim.classify(category, service, ClassificationSource.MANUAL, null);
            comments.save(new Comment(claim, user.id(), user.role(), trace, true));
            claims.flush();
            events.dispatchAfterCommit(ClaimEvent.of(ClaimEventType.CLASSIFICATION_CHANGED, claim, status, user));
        } else {
            throw ApiException.conflict("La classification ne peut plus être modifiée (statut " + status + ")");
        }
        return detail(claim, user);
    }

    // --- Affectation et réaffectation (BF15, BF23) -----------------------------------------------

    /**
     * Affecte à un service et éventuellement à un agent rattaché à ce service.
     * CLASSIFIED + agent → ASSIGNED. Sur ASSIGNED / IN_PROGRESS (réaffectation), le statut est
     * conservé et un agent est obligatoire. Chaque affectation est tracée en commentaire interne.
     */
    public ClaimDetail assign(CurrentUser user, Long claimId, AssignmentRequest request) {
        Claim claim = access.loadVisible(claimId, user);
        if (user.isClient()) {
            throw ApiException.forbidden("Un client ne peut pas affecter une réclamation");
        }
        ClaimStatus status = claim.getStatus();
        if (status == ClaimStatus.NEW) {
            throw ApiException.conflict("La réclamation doit d'abord être classée avant d'être affectée");
        }
        if (status != ClaimStatus.CLASSIFIED && status != ClaimStatus.ASSIGNED && status != ClaimStatus.IN_PROGRESS) {
            throw ApiException.conflict("Une réclamation " + status + " ne peut plus être affectée");
        }
        ProcessingService service = findService(request.serviceId());
        ServiceAgent agent = null;
        if (request.agentId() != null) {
            agent = serviceAgents.findByServiceIdAndAgentId(service.getId(), request.agentId())
                    .orElseThrow(() -> ApiException.badRequest("L'agent " + request.agentId()
                            + " n'est pas rattaché au service « " + service.getName() + " »"));
        } else if (status != ClaimStatus.CLASSIFIED) {
            throw ApiException.badRequest("Un agent doit être désigné pour réaffecter une réclamation en cours");
        }

        String trace = "Affectation : service « " + service.getName() + " »"
                + (agent == null ? ", sans agent" : ", agent " + agent.getAgentName() + " (" + agent.getAgentId() + ")");
        claim.assign(service, agent == null ? null : agent.getAgentId(), agent == null ? null : agent.getAgentName());
        comments.save(new Comment(claim, user.id(), user.role(), trace, true));
        if (status == ClaimStatus.CLASSIFIED && agent != null) {
            transition(claim, ClaimStatus.ASSIGNED, user, trace);
        } else {
            claims.flush();
        }
        events.dispatchAfterCommit(ClaimEvent.of(ClaimEventType.ASSIGNED, claim, status, user));
        return detail(claim, user);
    }

    // --- Utilitaires ------------------------------------------------------------------------------

    /** Vérifie (409 / 403), applique, historise et publie un changement de statut. */
    private void transition(Claim claim, ClaimStatus target, CurrentUser user, String note) {
        ClaimStatus from = claim.getStatus();
        ClaimWorkflow.requireTransition(from, target, user.role());
        if (user.isClient() && !Objects.equals(claim.getClientId(), user.id())) {
            throw ApiException.claimNotFound(claim.getId()); // défense en profondeur (déjà filtré par la visibilité)
        }
        claim.changeStatus(target);
        claims.flush();
        history.save(new StatusHistory(claim, from, target, user.id(), user.role(), note));
        events.dispatchAfterCommit(ClaimEvent.of(ClaimEventType.STATUS_CHANGED, claim, from, user));
    }

    static void requireOpen(Claim claim) {
        if (!claim.getStatus().isOpen()) {
            throw ApiException.conflict("La réclamation est " + claim.getStatus() + " : elle ne peut plus être modifiée");
        }
    }

    private ProcessingService findService(Long serviceId) {
        return services.findById(serviceId)
                .orElseThrow(() -> ApiException.badRequest("Service inconnu : " + serviceId));
    }

    private ClaimDetail detail(Claim claim, CurrentUser user) {
        List<Comment> visibleComments = ClaimAccessPolicy.canSeeInternalComments(user)
                ? comments.findByClaimIdOrderByCreatedAtAscIdAsc(claim.getId())
                : comments.findByClaimIdAndInternalFalseOrderByCreatedAtAscIdAsc(claim.getId());
        return DtoMapper.detail(claim,
                history.findByClaimIdOrderByChangedAtAscIdAsc(claim.getId()),
                visibleComments,
                attachments.findByClaimIdOrderByUploadedAtAscIdAsc(claim.getId()));
    }

    private static String nameOf(Category category) {
        return category == null ? "aucune" : category.getName();
    }

    private static String nameOf(ProcessingService service) {
        return service == null ? "aucun" : service.getName();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

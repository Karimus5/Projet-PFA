package ma.emsi.reclamations.claim.service;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import ma.emsi.reclamations.claim.ai.AiCallResult;
import ma.emsi.reclamations.claim.ai.AiClassifierClient.ClassifyResponse;
import ma.emsi.reclamations.claim.access.ClaimAccess;
import ma.emsi.reclamations.claim.access.ClaimAccessPolicy;
import ma.emsi.reclamations.claim.domain.AiFeedbackDecision;
import ma.emsi.reclamations.claim.domain.AiPrediction;
import ma.emsi.reclamations.claim.domain.AiPredictionOutcome;
import ma.emsi.reclamations.claim.domain.AiPredictionRepository;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
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

    private static final Logger log = LoggerFactory.getLogger(ClaimService.class);
    private static final int MAX_DESCRIPTION_LENGTH = 10_000;
    private static final DateTimeFormatter COMPLEMENT_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm 'UTC'");

    private final ClaimRepository claims;
    private final CategoryRepository categories;
    private final ProcessingServiceRepository services;
    private final ServiceAgentRepository serviceAgents;
    private final CommentRepository comments;
    private final StatusHistoryRepository history;
    private final AttachmentRepository attachments;
    private final AiPredictionRepository predictions;
    private final ClaimAccess access;
    private final ClaimEventDispatcher events;

    public ClaimService(ClaimRepository claims, CategoryRepository categories, ProcessingServiceRepository services,
                        ServiceAgentRepository serviceAgents, CommentRepository comments,
                        StatusHistoryRepository history, AttachmentRepository attachments,
                        AiPredictionRepository predictions, ClaimAccess access, ClaimEventDispatcher events) {
        this.claims = claims;
        this.categories = categories;
        this.services = services;
        this.serviceAgents = serviceAgents;
        this.comments = comments;
        this.history = history;
        this.attachments = attachments;
        this.predictions = predictions;
        this.access = access;
        this.events = events;
    }

    // --- Client : soumission (BF6) et complément (BF9) -------------------------------------------

    /**
     * Enregistre la réclamation en NEW (transaction validée au retour). Le classement automatique
     * est fait ensuite, hors de cette transaction, par {@link ClaimIntakeService}.
     */
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

    /**
     * @param toConfirm true : seulement les classements IA à confirmer ou corriger (agents et admins ;
     *                  403 pour un client), dans le périmètre de visibilité de l'appelant
     */
    @Transactional(readOnly = true)
    public PageResponse<ClaimSummary> list(CurrentUser user, ClaimStatus status, boolean toConfirm, Pageable pageable) {
        if (toConfirm && !user.role().isStaff()) {
            throw ApiException.forbidden("Le filtre « à confirmer » est réservé aux agents et aux administrateurs");
        }
        Specification<Claim> filter = access.visibleTo(user, status);
        if (toConfirm) {
            filter = filter.and(ClaimAccess.awaitingAiReview());
        }
        boolean staff = user.role().isStaff();
        return PageResponse.of(claims.findAll(filter, pageable).map(c -> DtoMapper.summary(c, staff)));
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
     * Si la classification venait de l'IA, le retour d'information est enregistré (BF14, BF20) :
     * CORRECTED, ou CONFIRMED si la catégorie et le service retenus sont ceux proposés.
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
            Optional<AiPrediction> prediction = predictions.findByClaimId(claim.getId()).filter(AiPrediction::isApplied);
            AiFeedbackDecision decision = prediction.map(p -> sameAsPrediction(p, category, service)
                    ? AiFeedbackDecision.CONFIRMED : AiFeedbackDecision.CORRECTED).orElse(null);
            if (prediction.isPresent()) {
                trace += " (proposition IA : " + prediction.get().getPredictedCategory() + ", "
                        + percent(prediction.get().getConfidence()) + ")";
            }
            claim.classify(category, service, ClassificationSource.MANUAL, null);
            comments.save(new Comment(claim, user.id(), user.role(), trace, true));
            String deciderName = deciderName(user);
            prediction.ifPresent(p -> p.recordDecision(decision, category.getName(), service.getName(),
                    user.id(), user.role(), deciderName));
            claims.flush();
            events.dispatchAfterCommit(ClaimEvent.of(ClaimEventType.CLASSIFICATION_CHANGED, claim, status, user));
            if (decision != null) {
                events.dispatchAfterCommit(ClaimEvent.of(ClaimEventType.CLASSIFICATION_FEEDBACK, claim, status, user,
                        decision.name()));
            }
        } else {
            throw ApiException.conflict("La classification ne peut plus être modifiée (statut " + status + ")");
        }
        return detail(claim, user);
    }

    /**
     * BF14 — un agent (de la réclamation) ou un admin confirme la catégorie et le service proposés
     * par l'IA. Statut et classification inchangés ; retour d'information CONFIRMED enregistré et
     * tracé en commentaire interne. 409 si la classification ne vient pas de l'IA, a déjà été
     * confirmée ou corrigée, ou si la réclamation n'est plus en cours de traitement.
     */
    public ClaimDetail confirmClassification(CurrentUser user, Long claimId) {
        Claim claim = access.loadVisible(claimId, user);
        if (!user.role().isStaff()) {
            throw ApiException.forbidden("Seul un agent ou un administrateur peut confirmer une classification");
        }
        ClaimStatus status = claim.getStatus();
        if (status != ClaimStatus.CLASSIFIED && status != ClaimStatus.ASSIGNED && status != ClaimStatus.IN_PROGRESS) {
            throw ApiException.conflict("La classification ne peut plus être confirmée (statut " + status + ")");
        }
        AiPrediction prediction = predictions.findByClaimId(claim.getId())
                .filter(p -> p.isApplied() && claim.getClassificationSource() == ClassificationSource.AI)
                .orElseThrow(() -> ApiException.conflict("Cette réclamation n'a pas été classée par l'IA"));
        if (prediction.getDecision() != null) {
            throw ApiException.conflict("La classification proposée par l'IA a déjà été "
                    + (prediction.getDecision() == AiFeedbackDecision.CONFIRMED ? "confirmée" : "corrigée"));
        }
        prediction.recordDecision(AiFeedbackDecision.CONFIRMED, nameOf(claim.getCategory()),
                nameOf(claim.getService()), user.id(), user.role(), deciderName(user));
        comments.save(new Comment(claim, user.id(), user.role(), "Classification IA confirmée : "
                + nameOf(claim.getCategory()) + ", service " + nameOf(claim.getService()), true));
        claims.flush();
        events.dispatchAfterCommit(ClaimEvent.of(ClaimEventType.CLASSIFICATION_FEEDBACK, claim, status, user,
                AiFeedbackDecision.CONFIRMED.name()));
        return detail(claim, user);
    }

    // --- Classement automatique (BF16-BF19), appelé par ClaimIntakeService ---------------------

    /**
     * Applique le résultat du service IA à une réclamation qui vient d'être créée (encore NEW).
     * <ul>
     *   <li>confiance ≥ seuil et catégorie connue : catégorie + service de la catégorie, source AI,
     *       score, NEW → CLASSIFIED historisé (auteur SYSTEM) ;</li>
     *   <li>sinon (confiance basse, catégorie inconnue, panne) : la réclamation reste NEW, avec un
     *       commentaire INTERNE qui explique pourquoi (jamais visible du client).</li>
     * </ul>
     * La prédiction est enregistrée dans tous les cas, sauf si l'IA est désactivée.
     */
    public void applyAiResult(Long claimId, AiCallResult result) {
        if (result instanceof AiCallResult.Disabled) {
            return;
        }
        Claim claim = claims.findById(claimId).orElseThrow(() -> ApiException.claimNotFound(claimId));
        if (claim.getStatus() != ClaimStatus.NEW || predictions.findByClaimId(claimId).isPresent()) {
            return; // déjà traitée entre-temps : on ne touche à rien
        }
        CurrentUser system = CurrentUser.SYSTEM;
        if (result instanceof AiCallResult.Failed failed) {
            predictions.save(AiPrediction.unavailable(claim, failed.reason().name()));
            internalNote(claim, "Classement automatique indisponible (" + failed.reason().label()
                    + ") : réclamation à classer manuellement.");
            return;
        }
        ClassifyResponse response = ((AiCallResult.Answered) result).response();
        Optional<Category> category = response.category() == null
                ? Optional.empty()
                : categories.findFirstByNameIgnoreCase(response.category().trim());
        if (response.category() != null && category.isEmpty()) {
            predictions.save(AiPrediction.answered(claim, AiPredictionOutcome.UNKNOWN_CATEGORY,
                    truncate(response.category()), truncate(response.service()), response.confidence(),
                    truncate(response.modelVersion()), validThreshold(response.threshold())));
            internalNote(claim, "Classement automatique non appliqué : la catégorie proposée « "
                    + truncate(response.category()) + " » n'existe pas dans le référentiel. À classer manuellement.");
            return;
        }
        if (category.isEmpty() || !Boolean.TRUE.equals(response.thresholdReached())) {
            predictions.save(AiPrediction.answered(claim, AiPredictionOutcome.LOW_CONFIDENCE,
                    category.map(Category::getName).orElse(null),
                    category.map(c -> c.getService().getName()).orElse(null),
                    response.confidence(), truncate(response.modelVersion()), validThreshold(response.threshold())));
            internalNote(claim, category.isEmpty()
                    ? "Classement automatique non appliqué : texte inexploitable. À classer manuellement."
                    : "Classement automatique non appliqué : confiance insuffisante ("
                    + category.get().getName() + ", " + percent(response.confidence())
                    + (response.threshold() == null ? "" : " < " + percent(response.threshold()))
                    + "). À classer manuellement.");
            return;
        }
        Category chosen = category.get();
        ProcessingService service = chosen.getService();
        claim.classify(chosen, service, ClassificationSource.AI, response.confidence());
        predictions.save(AiPrediction.answered(claim, AiPredictionOutcome.APPLIED, chosen.getName(),
                service.getName(), response.confidence(), truncate(response.modelVersion()),
                validThreshold(response.threshold())));
        // Note d'historique visible du client : sans la confiance (donnée du modèle, réservée au staff).
        transition(claim, ClaimStatus.CLASSIFIED, system, "Classement automatique : " + chosen.getName());
        events.dispatchAfterCommit(ClaimEvent.of(ClaimEventType.AUTO_CLASSIFIED, claim, ClaimStatus.NEW, system,
                String.format(Locale.ROOT, "confidence=%.2f;model=%s", response.confidence(),
                        truncate(response.modelVersion()))));
        log.info("Réclamation {} classée automatiquement : {} ({})", claim.getId(), chosen.getName(),
                percent(response.confidence()));
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
        AiPrediction prediction = ClaimAccessPolicy.canSeeInternalComments(user)
                ? predictions.findByClaimId(claim.getId()).orElse(null)
                : null; // le client ne voit pas le détail de la prédiction
        return DtoMapper.detail(claim,
                history.findByClaimIdOrderByChangedAtAscIdAsc(claim.getId()),
                visibleComments,
                attachments.findByClaimIdOrderByUploadedAtAscIdAsc(claim.getId()),
                prediction,
                ClaimAccessPolicy.canSeeInternalComments(user));
    }

    /**
     * Nom de l'auteur d'une décision, sans e-mail : pour un agent, le nom saisi par l'admin lors de
     * son rattachement à un service ; pour un admin, null (claim-service ne connaît pas son nom).
     */
    private String deciderName(CurrentUser user) {
        return user.isAgent()
                ? serviceAgents.findFirstByAgentIdOrderByIdAsc(user.id()).map(ServiceAgent::getAgentName).orElse(null)
                : null;
    }

    /** Seuil reçu du service IA, gardé seulement s'il est dans [0, 1]. */
    private static Double validThreshold(Double threshold) {
        return threshold != null && threshold >= 0 && threshold <= 1 ? threshold : null;
    }

    private void internalNote(Claim claim, String text) {
        comments.save(new Comment(claim, CurrentUser.SYSTEM.id(), CurrentUser.SYSTEM.role(), text, true));
        log.info("Réclamation {} laissée en classement manuel", claim.getId());
    }

    private static boolean sameAsPrediction(AiPrediction prediction, Category category, ProcessingService service) {
        return category.getName().equalsIgnoreCase(String.valueOf(prediction.getPredictedCategory()))
                && service.getName().equalsIgnoreCase(String.valueOf(prediction.getPredictedService()));
    }

    /**
     * 0.9234 → « 92 % ». Tronqué (pas arrondi) pour ne jamais afficher plus que la valeur réelle :
     * une confiance de 0.5695 sous un seuil de 0.57 donne « 56 % < 57 % », pas « 57 % < 57 % ».
     * La marge 1e-9 corrige l'imprécision des flottants (0.57 × 100 = 56.999…).
     */
    static String percent(Double value) {
        return value == null ? "?" : (long) Math.floor(value * 100 + 1e-9) + " %";
    }

    /** Valeurs reçues du service IA : bornées à la taille des colonnes. */
    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= 100 ? trimmed : trimmed.substring(0, 100);
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

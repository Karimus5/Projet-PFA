package ma.emsi.reclamations.claim.web;

import jakarta.validation.Valid;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import ma.emsi.reclamations.claim.domain.ClaimStatus;
import ma.emsi.reclamations.claim.error.ApiException;
import ma.emsi.reclamations.claim.security.CurrentUser;
import ma.emsi.reclamations.claim.service.AttachmentService;
import ma.emsi.reclamations.claim.service.ClaimService;
import ma.emsi.reclamations.claim.web.dto.Dtos.AssignmentRequest;
import ma.emsi.reclamations.claim.web.dto.Dtos.AttachmentResponse;
import ma.emsi.reclamations.claim.web.dto.Dtos.ClaimDetail;
import ma.emsi.reclamations.claim.web.dto.Dtos.ClaimSummary;
import ma.emsi.reclamations.claim.web.dto.Dtos.ClassificationRequest;
import ma.emsi.reclamations.claim.web.dto.Dtos.CommentRequest;
import ma.emsi.reclamations.claim.web.dto.Dtos.CommentResponse;
import ma.emsi.reclamations.claim.web.dto.Dtos.ComplementRequest;
import ma.emsi.reclamations.claim.web.dto.Dtos.CreateClaimRequest;
import ma.emsi.reclamations.claim.web.dto.Dtos.PageResponse;
import ma.emsi.reclamations.claim.web.dto.Dtos.StatusChangeRequest;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Réclamations : endpoints communs aux trois rôles. Les droits dépendent du rôle (X-User-Role) et
 * de la visibilité de la réclamation ; voir ClaimService et ClaimAccessPolicy.
 */
@RestController
@RequestMapping("/api/claims")
public class ClaimController {

    private static final int MAX_PAGE_SIZE = 100;

    private final ClaimService claimService;
    private final AttachmentService attachmentService;

    public ClaimController(ClaimService claimService, AttachmentService attachmentService) {
        this.claimService = claimService;
        this.attachmentService = attachmentService;
    }

    /** BF6 — CLIENT uniquement. */
    @PostMapping
    ResponseEntity<ClaimDetail> create(CurrentUser user, @Valid @RequestBody CreateClaimRequest request) {
        ClaimDetail created = claimService.create(user, request);
        return ResponseEntity.created(URI.create("/api/claims/" + created.id())).body(created);
    }

    /** BF7 / BF11 — réclamations visibles par l'appelant, plus récentes d'abord. */
    @GetMapping
    PageResponse<ClaimSummary> list(CurrentUser user,
                                    @RequestParam(required = false) ClaimStatus status,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw ApiException.badRequest("Pagination invalide : page ≥ 0 et 1 ≤ size ≤ " + MAX_PAGE_SIZE);
        }
        Sort sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        return claimService.list(user, status, PageRequest.of(page, size, sort));
    }

    /** BF8 — détail, historique, commentaires (sans les internes pour un client), pièces jointes. */
    @GetMapping("/{id:\\d+}")
    ClaimDetail get(CurrentUser user, @PathVariable Long id) {
        return claimService.get(user, id);
    }

    /** BF9 — complément d'information du client, réclamation ouverte. */
    @PostMapping("/{id:\\d+}/complements")
    ClaimDetail addComplement(CurrentUser user, @PathVariable Long id,
                              @Valid @RequestBody ComplementRequest request) {
        return claimService.addComplement(user, id, request.text());
    }

    /** BF13 — commentaire public (tous) ou interne (agents et admins). */
    @PostMapping("/{id:\\d+}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    CommentResponse addComment(CurrentUser user, @PathVariable Long id, @Valid @RequestBody CommentRequest request) {
        return claimService.addComment(user, id, request);
    }

    /** BF12 — changement de statut (règles : ClaimWorkflow). */
    @PatchMapping("/{id:\\d+}/status")
    ClaimDetail changeStatus(CurrentUser user, @PathVariable Long id,
                             @Valid @RequestBody StatusChangeRequest request) {
        return claimService.changeStatus(user, id, request);
    }

    /** Tri manuel NEW → CLASSIFIED (admin) ou correction de catégorie / service (BF14). */
    @PutMapping("/{id:\\d+}/classification")
    ClaimDetail classify(CurrentUser user, @PathVariable Long id, @Valid @RequestBody ClassificationRequest request) {
        return claimService.classify(user, id, request);
    }

    /** BF15 / BF23 — affectation ou réaffectation à un service et à un agent. */
    @PutMapping("/{id:\\d+}/assignment")
    ClaimDetail assign(CurrentUser user, @PathVariable Long id, @Valid @RequestBody AssignmentRequest request) {
        return claimService.assign(user, id, request);
    }

    /** Pièces jointes : multipart, partie « files » (un ou plusieurs fichiers). */
    @PostMapping(path = "/{id:\\d+}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    List<AttachmentResponse> upload(CurrentUser user, @PathVariable Long id,
                                    @RequestPart("files") List<MultipartFile> files) {
        return attachmentService.upload(user, id, files);
    }

    @GetMapping("/{id:\\d+}/attachments/{attachmentId:\\d+}")
    ResponseEntity<Resource> download(CurrentUser user, @PathVariable Long id, @PathVariable Long attachmentId) {
        AttachmentService.Download download = attachmentService.download(user, id, attachmentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.attachment().contentType()))
                .contentLength(download.attachment().size())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.attachment().fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(download.content());
    }
}

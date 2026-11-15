package ma.emsi.reclamations.claim.web;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import ma.emsi.reclamations.claim.security.CurrentUser;
import ma.emsi.reclamations.claim.service.AiFeedbackService;
import ma.emsi.reclamations.claim.web.dto.Dtos.AiStatsResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administration du classement automatique (ADMIN). Sous /api/claims/admin/** : la gateway exige
 * déjà le rôle ADMIN, AiFeedbackService le vérifie à nouveau (défense en profondeur).
 */
@RestController
public class AiAdminController {

    private final AiFeedbackService feedback;

    public AiAdminController(AiFeedbackService feedback) {
        this.feedback = feedback;
    }

    /** Chiffres du classement automatique pour le futur tableau de bord (BF25). */
    @GetMapping("/api/claims/admin/ai-stats")
    AiStatsResponse stats(CurrentUser user) {
        return feedback.stats(user);
    }

    /** BF20 — export CSV des confirmations et corrections, pour réentraîner le modèle. */
    @GetMapping("/api/claims/admin/ai-feedback/export")
    ResponseEntity<byte[]> export(CurrentUser user) {
        byte[] body = feedback.exportCsv(user).getBytes(StandardCharsets.UTF_8);
        String fileName = "ai-feedback-" + LocalDate.now(ZoneOffset.UTC) + ".csv";
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(body);
    }
}

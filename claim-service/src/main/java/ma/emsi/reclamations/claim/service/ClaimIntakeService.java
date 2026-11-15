package ma.emsi.reclamations.claim.service;

import ma.emsi.reclamations.claim.ai.AiCallResult;
import ma.emsi.reclamations.claim.ai.AiClassificationGateway;
import ma.emsi.reclamations.claim.security.CurrentUser;
import ma.emsi.reclamations.claim.web.dto.Dtos.ClaimDetail;
import ma.emsi.reclamations.claim.web.dto.Dtos.CreateClaimRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Soumission d'une réclamation avec classement automatique (BF6, BF16-BF19). Volontairement SANS
 * transaction englobante :
 * <ol>
 *   <li>{@link ClaimService#create} enregistre la réclamation en NEW et valide la transaction ;</li>
 *   <li>le service IA est appelé hors transaction (aucune connexion à la base n'est retenue
 *       pendant l'appel, au plus 2 s) ;</li>
 *   <li>{@link ClaimService#applyAiResult} applique le résultat dans une seconde transaction.</li>
 * </ol>
 * Une erreur aux étapes 2 ou 3 laisse la réclamation en NEW (classement manuel) : la création ne
 * peut jamais échouer à cause de l'IA.
 */
@Service
public class ClaimIntakeService {

    private static final Logger log = LoggerFactory.getLogger(ClaimIntakeService.class);

    private final ClaimService claimService;
    private final AiClassificationGateway ai;

    public ClaimIntakeService(ClaimService claimService, AiClassificationGateway ai) {
        this.claimService = claimService;
        this.ai = ai;
    }

    public ClaimDetail submit(CurrentUser user, CreateClaimRequest request) {
        ClaimDetail created = claimService.create(user, request); // 403 si l'appelant n'est pas client
        try {
            AiCallResult result = ai.classify(created.title(), created.description());
            if (result instanceof AiCallResult.Disabled) {
                return created;
            }
            claimService.applyAiResult(created.id(), result);
            return claimService.get(user, created.id());
        } catch (RuntimeException e) {
            log.error("Classement automatique non appliqué à la réclamation {} : elle reste NEW", created.id(), e);
            return created;
        }
    }
}

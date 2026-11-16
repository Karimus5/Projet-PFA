package ma.emsi.reclamations.claim.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import ma.emsi.reclamations.claim.domain.AiPrediction;
import ma.emsi.reclamations.claim.domain.AiPredictionOutcome;
import ma.emsi.reclamations.claim.domain.Category;
import ma.emsi.reclamations.claim.domain.Claim;
import ma.emsi.reclamations.claim.domain.ClaimStatus;
import ma.emsi.reclamations.claim.domain.ClassificationSource;
import ma.emsi.reclamations.claim.domain.ProcessingService;
import ma.emsi.reclamations.claim.domain.StatusHistory;
import ma.emsi.reclamations.claim.security.UserRole;
import ma.emsi.reclamations.claim.web.dto.Dtos.ClaimDetail;
import ma.emsi.reclamations.claim.web.dto.Dtos.ClaimSummary;
import org.junit.jupiter.api.Test;

/** Étape 7c : données du modèle selon le rôle (unitaire, sans base). */
class DtoMapperTest {

    private static Claim aiClaim() {
        ProcessingService service = new ProcessingService("Livraison", null);
        Claim claim = new Claim("Colis", "Mon colis n'est jamais arrivé", 2L, "client@test.local");
        claim.classify(new Category("Livraison", null, service), service, ClassificationSource.AI, 0.9234);
        return claim;
    }

    @Test
    void clientSummaryHasNoModelData() {
        ClaimSummary forClient = DtoMapper.summary(aiClaim(), false);
        assertThat(forClient.classificationSource()).isNull();
        assertThat(forClient.confidenceScore()).isNull();
        assertThat(forClient.autoClassified()).isTrue();

        ClaimSummary forStaff = DtoMapper.summary(aiClaim(), true);
        assertThat(forStaff.classificationSource()).isEqualTo(ClassificationSource.AI);
        assertThat(forStaff.confidenceScore()).isEqualTo(0.9234);
        assertThat(forStaff.autoClassified()).isTrue();
    }

    @Test
    void clientDetailHasNoPredictionEvenIfOneIsGiven() {
        Claim claim = aiClaim();
        AiPrediction prediction = AiPrediction.answered(claim, AiPredictionOutcome.APPLIED, "Livraison", "Livraison",
                0.9234, "1.0.0", 0.57);

        ClaimDetail forClient = DtoMapper.detail(claim, List.of(), List.of(), List.of(), prediction, false);
        assertThat(forClient.aiPrediction()).isNull();
        assertThat(forClient.confidenceScore()).isNull();
        assertThat(forClient.classificationSource()).isNull();
        assertThat(forClient.autoClassified()).isTrue();

        ClaimDetail forStaff = DtoMapper.detail(claim, List.of(), List.of(), List.of(), prediction, true);
        assertThat(forStaff.aiPrediction().threshold()).isEqualTo(0.57);
        assertThat(forStaff.aiPrediction().confidence()).isEqualTo(0.9234);
    }

    @Test
    void manualClassificationIsNotAutoClassified() {
        Claim claim = aiClaim();
        claim.classify(claim.getCategory(), claim.getService(), ClassificationSource.MANUAL, null);
        assertThat(DtoMapper.summary(claim, false).autoClassified()).isFalse();
    }

    @Test
    void legacyAutoClassificationNoteLosesItsConfidenceForClients() {
        StatusHistory legacy = new StatusHistory(aiClaim(), ClaimStatus.NEW, ClaimStatus.CLASSIFIED, 0L, UserRole.SYSTEM,
                "Classement automatique : Livraison (99 %)");

        assertThat(DtoMapper.history(legacy, false).note()).isEqualTo("Classement automatique : Livraison");
        assertThat(DtoMapper.history(legacy, true).note()).isEqualTo("Classement automatique : Livraison (99 %)");
    }

    @Test
    void notesOfRealUsersAreNeverRewritten() {
        StatusHistory byAgent = new StatusHistory(aiClaim(), ClaimStatus.IN_PROGRESS, ClaimStatus.RESOLVED, 3L,
                UserRole.AGENT, "Remboursé à 100 %");
        assertThat(DtoMapper.history(byAgent, false).note()).isEqualTo("Remboursé à 100 %");
    }
}

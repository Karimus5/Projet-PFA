package ma.emsi.reclamations.claim.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import ma.emsi.reclamations.claim.security.UserRole;

/**
 * Prédiction du service IA pour une réclamation (classe « Prediction » du diagramme de classes) et
 * retour d'information de l'agent (catégorie proposée, confiance, décision finale, auteur, date).
 * Une réclamation porte au plus une prédiction. Les noms sont copiés (voir migration V3).
 */
@Entity
@Table(name = "ai_predictions")
public class AiPrediction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false, updatable = false, unique = true)
    private Claim claim;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private AiPredictionOutcome outcome;

    /** Motif d'indisponibilité (UNAVAILABLE uniquement), voir AiFailureReason. */
    @Column(name = "failure_reason", length = 30, updatable = false)
    private String failureReason;

    @Column(name = "predicted_category", length = 100, updatable = false)
    private String predictedCategory;

    @Column(name = "predicted_service", length = 100, updatable = false)
    private String predictedService;

    @Column(updatable = false)
    private Double confidence;

    @Column(name = "model_version", length = 20, updatable = false)
    private String modelVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private AiFeedbackDecision decision;

    @Column(name = "final_category", length = 100)
    private String finalCategory;

    @Column(name = "final_service", length = 100)
    private String finalService;

    @Column(name = "decided_by_id")
    private Long decidedById;

    @Enumerated(EnumType.STRING)
    @Column(name = "decided_by_role", length = 20)
    private UserRole decidedByRole;

    @Column(name = "decided_at")
    private Instant decidedAt;

    protected AiPrediction() {
        // requis par JPA
    }

    private AiPrediction(Claim claim, AiPredictionOutcome outcome, String failureReason, String predictedCategory,
                         String predictedService, Double confidence, String modelVersion) {
        this.claim = claim;
        this.outcome = outcome;
        this.failureReason = failureReason;
        this.predictedCategory = predictedCategory;
        this.predictedService = predictedService;
        this.confidence = confidence;
        this.modelVersion = modelVersion;
    }

    public static AiPrediction answered(Claim claim, AiPredictionOutcome outcome, String predictedCategory,
                                        String predictedService, Double confidence, String modelVersion) {
        return new AiPrediction(claim, outcome, null, predictedCategory, predictedService, confidence, modelVersion);
    }

    public static AiPrediction unavailable(Claim claim, String failureReason) {
        return new AiPrediction(claim, AiPredictionOutcome.UNAVAILABLE, failureReason, null, null, null, null);
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    /** Enregistre (ou remplace par la plus récente) la décision de l'agent / admin. */
    public void recordDecision(AiFeedbackDecision decision, String finalCategory, String finalService,
                               Long decidedById, UserRole decidedByRole) {
        this.decision = decision;
        this.finalCategory = finalCategory;
        this.finalService = finalService;
        this.decidedById = decidedById;
        this.decidedByRole = decidedByRole;
        this.decidedAt = Instant.now();
    }

    public boolean isApplied() {
        return outcome == AiPredictionOutcome.APPLIED;
    }

    public Long getId() {
        return id;
    }

    public Claim getClaim() {
        return claim;
    }

    public AiPredictionOutcome getOutcome() {
        return outcome;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public String getPredictedCategory() {
        return predictedCategory;
    }

    public String getPredictedService() {
        return predictedService;
    }

    public Double getConfidence() {
        return confidence;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public AiFeedbackDecision getDecision() {
        return decision;
    }

    public String getFinalCategory() {
        return finalCategory;
    }

    public String getFinalService() {
        return finalService;
    }

    public Long getDecidedById() {
        return decidedById;
    }

    public UserRole getDecidedByRole() {
        return decidedByRole;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }
}

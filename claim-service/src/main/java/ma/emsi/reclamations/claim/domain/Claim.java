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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

/**
 * Réclamation d'un client. Le statut ne change que via {@link #changeStatus}, appelé par
 * ClaimService après vérification par {@link ClaimWorkflow} ; chaque changement est historisé
 * (StatusHistory) par le service.
 */
@Entity
@Table(name = "claims")
public class Claim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 10000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClaimStatus status = ClaimStatus.NEW;

    /** Id du client dans auth-service (sub du JWT). */
    @Column(name = "client_id", nullable = false, updatable = false)
    private Long clientId;

    @Column(name = "client_email")
    private String clientEmail;

    @Column(name = "assigned_agent_id")
    private Long assignedAgentId;

    @Column(name = "assigned_agent_name", length = 100)
    private String assignedAgentName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id")
    private ProcessingService service;

    @Enumerated(EnumType.STRING)
    @Column(name = "classification_source", length = 10)
    private ClassificationSource classificationSource;

    /** Score de confiance de l'IA (0..1) ; null pour une classification manuelle. */
    @Column(name = "confidence_score")
    private Double confidenceScore;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Verrou optimiste : deux modifications concurrentes → la seconde échoue (409). */
    @Version
    @Column(nullable = false)
    private long version;

    protected Claim() {
        // requis par JPA
    }

    public Claim(String title, String description, Long clientId, String clientEmail) {
        this.title = title;
        this.description = description;
        this.clientId = clientId;
        this.clientEmail = clientEmail;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void changeStatus(ClaimStatus target) {
        this.status = target;
    }

    public void classify(Category category, ProcessingService service, ClassificationSource source,
                         Double confidenceScore) {
        this.category = category;
        this.service = service;
        this.classificationSource = source;
        this.confidenceScore = confidenceScore;
    }

    public void assign(ProcessingService service, Long agentId, String agentName) {
        this.service = service;
        this.assignedAgentId = agentId;
        this.assignedAgentName = agentName;
    }

    public void appendToDescription(String complement) {
        this.description = this.description + complement;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public ClaimStatus getStatus() {
        return status;
    }

    public Long getClientId() {
        return clientId;
    }

    public String getClientEmail() {
        return clientEmail;
    }

    public Long getAssignedAgentId() {
        return assignedAgentId;
    }

    public String getAssignedAgentName() {
        return assignedAgentName;
    }

    public Category getCategory() {
        return category;
    }

    public ProcessingService getService() {
        return service;
    }

    public ClassificationSource getClassificationSource() {
        return classificationSource;
    }

    public Double getConfidenceScore() {
        return confidenceScore;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

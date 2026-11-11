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
import jakarta.persistence.Table;
import java.time.Instant;
import ma.emsi.reclamations.claim.security.UserRole;

/** Une ligne par changement de statut (oldStatus null = création de la réclamation). Immuable. */
@Entity
@Table(name = "status_history")
public class StatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false, updatable = false)
    private Claim claim;

    @Enumerated(EnumType.STRING)
    @Column(name = "old_status", length = 20, updatable = false)
    private ClaimStatus oldStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 20, updatable = false)
    private ClaimStatus newStatus;

    @Column(name = "author_id", nullable = false, updatable = false)
    private Long authorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "author_role", nullable = false, length = 20, updatable = false)
    private UserRole authorRole;

    @Column(length = 500, updatable = false)
    private String note;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    protected StatusHistory() {
        // requis par JPA
    }

    public StatusHistory(Claim claim, ClaimStatus oldStatus, ClaimStatus newStatus,
                         Long authorId, UserRole authorRole, String note) {
        this.claim = claim;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.authorId = authorId;
        this.authorRole = authorRole;
        this.note = note;
    }

    @PrePersist
    void onCreate() {
        if (changedAt == null) {
            changedAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public ClaimStatus getOldStatus() {
        return oldStatus;
    }

    public ClaimStatus getNewStatus() {
        return newStatus;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public UserRole getAuthorRole() {
        return authorRole;
    }

    public String getNote() {
        return note;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}

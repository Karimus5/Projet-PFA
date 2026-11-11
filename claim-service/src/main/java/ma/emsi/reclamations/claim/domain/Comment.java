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

/** Commentaire sur une réclamation. internal = visible uniquement des agents et des admins (BF13). */
@Entity
@Table(name = "claim_comments")
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false, updatable = false)
    private Claim claim;

    @Column(name = "author_id", nullable = false, updatable = false)
    private Long authorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "author_role", nullable = false, length = 20, updatable = false)
    private UserRole authorRole;

    @Column(nullable = false, length = 2000, updatable = false)
    private String content;

    @Column(nullable = false, updatable = false)
    private boolean internal;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Comment() {
        // requis par JPA
    }

    public Comment(Claim claim, Long authorId, UserRole authorRole, String content, boolean internal) {
        this.claim = claim;
        this.authorId = authorId;
        this.authorRole = authorRole;
        this.content = content;
        this.internal = internal;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public UserRole getAuthorRole() {
        return authorRole;
    }

    public String getContent() {
        return content;
    }

    public boolean isInternal() {
        return internal;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

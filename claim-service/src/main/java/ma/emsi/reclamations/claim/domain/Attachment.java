package ma.emsi.reclamations.claim.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Pièce jointe. fileName = nom d'origine ASSAINI (affichage / téléchargement uniquement) ;
 * storagePath = chemin relatif généré (claimId/uuid.ext) sous le dossier de stockage : le nom
 * fourni par l'utilisateur n'est jamais utilisé pour écrire sur le disque.
 */
@Entity
@Table(name = "claim_attachments")
public class Attachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false, updatable = false)
    private Claim claim;

    @Column(name = "file_name", nullable = false, length = 255, updatable = false)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 100, updatable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Column(name = "storage_path", nullable = false, length = 255, updatable = false)
    private String storagePath;

    @Column(name = "uploaded_by_id", nullable = false, updatable = false)
    private Long uploadedById;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    protected Attachment() {
        // requis par JPA
    }

    public Attachment(Claim claim, String fileName, String contentType, long sizeBytes,
                      String storagePath, Long uploadedById) {
        this.claim = claim;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.storagePath = storagePath;
        this.uploadedById = uploadedById;
    }

    @PrePersist
    void onCreate() {
        if (uploadedAt == null) {
            uploadedAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Claim getClaim() {
        return claim;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public Long getUploadedById() {
        return uploadedById;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }
}

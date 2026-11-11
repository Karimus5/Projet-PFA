package ma.emsi.reclamations.claim.storage;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * Configuration des pièces jointes (config-repo/claim-service.yml, préfixe claims.attachments).
 *
 * @param storageDir       dossier de stockage, hors du dépôt (variable CLAIMS_STORAGE_DIR)
 * @param maxFileSize      taille maximale d'un fichier (5 Mo)
 * @param maxFilesPerClaim nombre maximal de pièces jointes par réclamation (5)
 */
@ConfigurationProperties(prefix = "claims.attachments")
public record AttachmentProperties(Path storageDir, DataSize maxFileSize, Integer maxFilesPerClaim) {

    public AttachmentProperties {
        if (storageDir == null) {
            throw new IllegalStateException("claims.attachments.storage-dir doit être configuré");
        }
        maxFileSize = maxFileSize == null ? DataSize.ofMegabytes(5) : maxFileSize;
        maxFilesPerClaim = maxFilesPerClaim == null ? 5 : maxFilesPerClaim;
    }
}

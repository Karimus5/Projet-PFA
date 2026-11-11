package ma.emsi.reclamations.claim.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Stockage des pièces jointes sur disque, sous {@code claims.attachments.storage-dir} :
 * {@code <claimId>/<uuid>.<ext>}. Le nom fourni par l'utilisateur n'entre jamais dans le chemin ;
 * tout chemin résolu est vérifié pour rester DANS le dossier racine.
 */
@Component
public class AttachmentStorage {

    private static final Logger log = LoggerFactory.getLogger(AttachmentStorage.class);

    private final Path root;

    public AttachmentStorage(AttachmentProperties properties) {
        this.root = properties.storageDir().toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("Impossible de créer le dossier des pièces jointes " + root, e);
        }
        log.info("Pièces jointes stockées dans {}", root);
    }

    /** Écrit le contenu et renvoie le chemin RELATIF à enregistrer en base. */
    public String store(Long claimId, AllowedFileType type, InputStream content) throws IOException {
        String relative = claimId + "/" + UUID.randomUUID() + "." + type.storageExtension();
        Path target = resolve(relative);
        Files.createDirectories(target.getParent());
        Files.copy(content, target); // échoue si le fichier existe déjà (UUID : ne devrait jamais arriver)
        return relative;
    }

    public Resource load(String relativePath) {
        Path path = resolve(relativePath);
        if (!Files.isRegularFile(path)) {
            throw new UncheckedIOException(new IOException("Fichier absent du stockage : " + relativePath));
        }
        return new PathResource(path);
    }

    /** Suppression silencieuse (nettoyage après un échec d'enregistrement en base). */
    public void deleteQuietly(String relativePath) {
        try {
            Files.deleteIfExists(resolve(relativePath));
        } catch (IOException | RuntimeException e) {
            log.warn("Impossible de supprimer la pièce jointe {}", relativePath, e);
        }
    }

    private Path resolve(String relativePath) {
        Path path = root.resolve(relativePath).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Chemin de pièce jointe hors du dossier de stockage");
        }
        return path;
    }
}

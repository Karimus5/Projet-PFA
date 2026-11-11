package ma.emsi.reclamations.claim.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import ma.emsi.reclamations.claim.access.ClaimAccess;
import ma.emsi.reclamations.claim.domain.Attachment;
import ma.emsi.reclamations.claim.domain.AttachmentRepository;
import ma.emsi.reclamations.claim.domain.Claim;
import ma.emsi.reclamations.claim.error.ApiException;
import ma.emsi.reclamations.claim.security.CurrentUser;
import ma.emsi.reclamations.claim.storage.AllowedFileType;
import ma.emsi.reclamations.claim.storage.AttachmentProperties;
import ma.emsi.reclamations.claim.storage.AttachmentStorage;
import ma.emsi.reclamations.claim.storage.FileNameSanitizer;
import ma.emsi.reclamations.claim.web.dto.Dtos.AttachmentResponse;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * Pièces jointes (BF6 : facultatives) : PDF, PNG, JPG ; 5 Mo par fichier ; 5 par réclamation.
 * Envoi et téléchargement suivent EXACTEMENT les règles de visibilité de la réclamation (404).
 * Envoi autorisé tant que la réclamation est ouverte (ni CLOSED ni REJECTED).
 */
@Service
@Transactional
public class AttachmentService {

    private final ClaimAccess access;
    private final AttachmentRepository attachments;
    private final AttachmentStorage storage;
    private final AttachmentProperties properties;

    public AttachmentService(ClaimAccess access, AttachmentRepository attachments, AttachmentStorage storage,
                             AttachmentProperties properties) {
        this.access = access;
        this.attachments = attachments;
        this.storage = storage;
        this.properties = properties;
    }

    /** Pièce jointe et son contenu, pour le téléchargement. */
    public record Download(AttachmentResponse attachment, Resource content) {
    }

    public List<AttachmentResponse> upload(CurrentUser user, Long claimId, List<MultipartFile> files) {
        Claim claim = access.loadVisible(claimId, user);
        ClaimService.requireOpen(claim);
        List<MultipartFile> nonEmpty = files == null ? List.of()
                : files.stream().filter(f -> f != null && !f.isEmpty()).toList();
        if (nonEmpty.isEmpty()) {
            throw ApiException.badRequest("Aucun fichier reçu (partie « files » vide)");
        }
        long existing = attachments.countByClaimId(claimId);
        if (existing + nonEmpty.size() > properties.maxFilesPerClaim()) {
            throw ApiException.conflict(properties.maxFilesPerClaim() + " pièces jointes maximum par réclamation (déjà "
                    + existing + ")");
        }

        // Validation de TOUS les fichiers avant d'en écrire un seul.
        List<AllowedFileType> types = new ArrayList<>();
        for (MultipartFile file : nonEmpty) {
            types.add(validate(file));
        }

        List<AttachmentResponse> saved = new ArrayList<>();
        for (int i = 0; i < nonEmpty.size(); i++) {
            MultipartFile file = nonEmpty.get(i);
            AllowedFileType type = types.get(i);
            String storagePath;
            try (InputStream in = file.getInputStream()) {
                storagePath = storage.store(claimId, type, in);
            } catch (IOException e) {
                throw new UncheckedIOException("Échec d'écriture de la pièce jointe", e);
            }
            deleteFileIfRolledBack(storagePath);
            Attachment attachment = attachments.save(new Attachment(claim,
                    FileNameSanitizer.sanitize(file.getOriginalFilename()), type.contentType(),
                    file.getSize(), storagePath, user.id()));
            saved.add(DtoMapper.attachment(attachment));
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public Download download(CurrentUser user, Long claimId, Long attachmentId) {
        access.loadVisible(claimId, user);
        Attachment attachment = attachments.findByIdAndClaimId(attachmentId, claimId)
                .orElseThrow(() -> ApiException.notFound("Pièce jointe introuvable : " + attachmentId));
        return new Download(DtoMapper.attachment(attachment), storage.load(attachment.getStoragePath()));
    }

    private AllowedFileType validate(MultipartFile file) {
        String name = FileNameSanitizer.sanitize(file.getOriginalFilename());
        if (file.getSize() > properties.maxFileSize().toBytes()) {
            throw ApiException.badRequest("Fichier trop volumineux : " + name + " ("
                    + properties.maxFileSize().toMegabytes() + " Mo maximum)");
        }
        byte[] header;
        try (InputStream in = file.getInputStream()) {
            header = in.readNBytes(AllowedFileType.HEADER_LENGTH);
        } catch (IOException e) {
            throw ApiException.badRequest("Fichier illisible : " + name);
        }
        AllowedFileType type = AllowedFileType.detect(header)
                .orElseThrow(() -> ApiException.badRequest("Type de fichier non autorisé : " + name
                        + " (PDF, PNG ou JPG uniquement)"));
        if (!type.acceptsExtension(name)) {
            throw ApiException.badRequest("L'extension de " + name + " ne correspond pas à son contenu ("
                    + type.contentType() + ")");
        }
        return type;
    }

    /** Si la transaction est annulée, le fichier déjà écrit sur le disque est supprimé. */
    private void deleteFileIfRolledBack(String storagePath) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        storage.deleteQuietly(storagePath);
                    }
                }
            });
        }
    }
}

package ma.emsi.reclamations.claim.storage;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Types de fichiers acceptés. Le type est déterminé par la SIGNATURE binaire (premiers octets),
 * pas par le Content-Type envoyé par le navigateur ; l'extension du nom doit en plus correspondre.
 */
public enum AllowedFileType {
    PDF("application/pdf", "pdf", Set.of("pdf"), new byte[] {'%', 'P', 'D', 'F', '-'}),
    PNG("image/png", "png", Set.of("png"), new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}),
    JPEG("image/jpeg", "jpg", Set.of("jpg", "jpeg"), new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});

    /** Nombre d'octets à lire pour reconnaître n'importe quel type ci-dessus. */
    public static final int HEADER_LENGTH = 8;

    private final String contentType;
    private final String storageExtension;
    private final Set<String> extensions;
    private final byte[] signature;

    AllowedFileType(String contentType, String storageExtension, Set<String> extensions, byte[] signature) {
        this.contentType = contentType;
        this.storageExtension = storageExtension;
        this.extensions = extensions;
        this.signature = signature;
    }

    public static Optional<AllowedFileType> detect(byte[] header) {
        return Arrays.stream(values()).filter(type -> type.matches(header)).findFirst();
    }

    public boolean acceptsExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 && extensions.contains(fileName.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    private boolean matches(byte[] header) {
        if (header.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (header[i] != signature[i]) {
                return false;
            }
        }
        return true;
    }

    public String contentType() {
        return contentType;
    }

    public String storageExtension() {
        return storageExtension;
    }
}

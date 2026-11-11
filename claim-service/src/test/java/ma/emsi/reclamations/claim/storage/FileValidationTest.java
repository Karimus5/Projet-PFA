package ma.emsi.reclamations.claim.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** Assainissement des noms et détection du type réel des fichiers. */
class FileValidationTest {

    @Test
    void sanitizeRemovesPathsAndSpecialCharacters() {
        assertThat(FileNameSanitizer.sanitize("../../etc/passwd")).isEqualTo("passwd");
        assertThat(FileNameSanitizer.sanitize("C:\\Users\\x\\facture.pdf")).isEqualTo("facture.pdf");
        assertThat(FileNameSanitizer.sanitize("fac<ture>|\"*?.pdf")).isEqualTo("fac_ture_.pdf");
        assertThat(FileNameSanitizer.sanitize("reçu été 2026.png")).isEqualTo("reçu été 2026.png");
        assertThat(FileNameSanitizer.sanitize("..pdf")).isEqualTo("pdf");
        assertThat(FileNameSanitizer.sanitize("a\u0000b\nc.jpg")).isEqualTo("a_b_c.jpg");
        assertThat(FileNameSanitizer.sanitize(null)).isEqualTo("fichier");
        assertThat(FileNameSanitizer.sanitize("...")).isEqualTo("fichier");
    }

    @Test
    void sanitizeKeepsExtensionWhenTruncating() {
        String sanitized = FileNameSanitizer.sanitize("a".repeat(300) + ".pdf");
        assertThat(sanitized).hasSize(100).endsWith(".pdf");
    }

    @Test
    void detectByMagicBytes() {
        assertThat(AllowedFileType.detect("%PDF-1.7".getBytes(StandardCharsets.US_ASCII))).contains(AllowedFileType.PDF);
        assertThat(AllowedFileType.detect(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}))
                .contains(AllowedFileType.PNG);
        assertThat(AllowedFileType.detect(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0}))
                .contains(AllowedFileType.JPEG);
        assertThat(AllowedFileType.detect("MZ\u0090\u0000".getBytes(StandardCharsets.ISO_8859_1))).isEmpty();
        assertThat(AllowedFileType.detect(new byte[] {'%', 'P'})).isEmpty();
    }

    @Test
    void extensionMustMatchDetectedType() {
        assertThat(AllowedFileType.JPEG.acceptsExtension("photo.JPEG")).isTrue();
        assertThat(AllowedFileType.JPEG.acceptsExtension("photo.jpg")).isTrue();
        assertThat(AllowedFileType.JPEG.acceptsExtension("photo.png")).isFalse();
        assertThat(AllowedFileType.PDF.acceptsExtension("facture")).isFalse();
    }
}

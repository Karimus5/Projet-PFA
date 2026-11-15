package ma.emsi.reclamations.claim.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Échappement des champs de l'export CSV (BF20). */
class CsvFieldTest {

    @Test
    void quotesAndDoublesInnerQuotes() {
        assertThat(AiFeedbackService.field("Colis \"perdu\", vraiment")).isEqualTo("\"Colis \"\"perdu\"\", vraiment\"");
        assertThat(AiFeedbackService.field("ligne 1\nligne 2")).isEqualTo("\"ligne 1\nligne 2\"");
        assertThat(AiFeedbackService.field(null)).isEqualTo("\"\"");
    }

    @Test
    void neutralizesSpreadsheetFormulas() {
        assertThat(AiFeedbackService.field("=HYPERLINK(\"http://x\")")).isEqualTo("\"'=HYPERLINK(\"\"http://x\"\")\"");
        assertThat(AiFeedbackService.field("+33 6")).startsWith("\"'+");
        assertThat(AiFeedbackService.field("@SUM(A1)")).startsWith("\"'@");
        assertThat(AiFeedbackService.field("Facture = 200 DH")).isEqualTo("\"Facture = 200 DH\"");
    }

    @Test
    void percentFormat() {
        assertThat(ClaimService.percent(0.9234)).isEqualTo("92 %");
        assertThat(ClaimService.percent(0.57)).isEqualTo("57 %");      // pas 56 % (0.57 × 100 = 56.999…)
        assertThat(ClaimService.percent(0.5695)).isEqualTo("56 %");    // sous le seuil : jamais « 57 % < 57 % »
        assertThat(ClaimService.percent(1.0)).isEqualTo("100 %");
        assertThat(ClaimService.percent(null)).isEqualTo("?");
    }
}

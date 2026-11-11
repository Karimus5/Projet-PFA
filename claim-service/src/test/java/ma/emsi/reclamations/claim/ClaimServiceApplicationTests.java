package ma.emsi.reclamations.claim;

import static org.assertj.core.api.Assertions.assertThat;

import ma.emsi.reclamations.claim.domain.CategoryRepository;
import ma.emsi.reclamations.claim.domain.ProcessingServiceRepository;
import ma.emsi.reclamations.claim.event.ClaimEventPublisher;
import ma.emsi.reclamations.claim.event.LoggingClaimEventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Le contexte démarre, les migrations Flyway passent (schéma + données initiales). */
@SpringBootTest
class ClaimServiceApplicationTests {

    @Autowired
    private ProcessingServiceRepository services;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private ClaimEventPublisher publisher;

    @Test
    void contextLoadsWithSeedDataAndDefaultEventPublisher() {
        assertThat(services.findAllByOrderByNameAsc()).extracting("name")
                .contains("Facturation", "Support technique", "Livraison");
        assertThat(categories.findAllByOrderByNameAsc()).extracting("name")
                .contains("Facturation", "Support technique", "Livraison");
        assertThat(publisher).isInstanceOf(LoggingClaimEventPublisher.class);
    }
}

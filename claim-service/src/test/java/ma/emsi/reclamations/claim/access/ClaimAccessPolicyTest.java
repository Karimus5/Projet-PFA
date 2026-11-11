package ma.emsi.reclamations.claim.access;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import ma.emsi.reclamations.claim.domain.Claim;
import ma.emsi.reclamations.claim.domain.ProcessingService;
import ma.emsi.reclamations.claim.security.CurrentUser;
import ma.emsi.reclamations.claim.security.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Règles de visibilité, sans base de données. */
class ClaimAccessPolicyTest {

    private static final long OWNER = 10L;
    private static final long BILLING_SERVICE = 1L;
    private static final long DELIVERY_SERVICE = 3L;

    private final CurrentUser owner = new CurrentUser(OWNER, "owner@test", UserRole.CLIENT);
    private final CurrentUser otherClient = new CurrentUser(11L, "other@test", UserRole.CLIENT);
    private final CurrentUser agent = new CurrentUser(20L, "agent@test", UserRole.AGENT);
    private final CurrentUser admin = new CurrentUser(1L, "admin@test", UserRole.ADMIN);

    @Test
    void clientSeesOnlyOwnClaims() {
        Claim claim = claim(null, null);
        assertThat(ClaimAccessPolicy.canView(claim, owner, List.of())).isTrue();
        assertThat(ClaimAccessPolicy.canView(claim, otherClient, List.of())).isFalse();
    }

    @Test
    void clientWithSameIdAsAnAgentServiceStillSeesOnlyOwnClaims() {
        // Les services d'agent passés par erreur pour un client sont ignorés.
        Claim claim = claim(BILLING_SERVICE, null);
        assertThat(ClaimAccessPolicy.canView(claim, otherClient, List.of(BILLING_SERVICE))).isFalse();
    }

    @Test
    void agentSeesClaimsOfHisServices() {
        Claim claim = claim(BILLING_SERVICE, null);
        assertThat(ClaimAccessPolicy.canView(claim, agent, List.of(BILLING_SERVICE))).isTrue();
        assertThat(ClaimAccessPolicy.canView(claim, agent, List.of(DELIVERY_SERVICE))).isFalse();
    }

    @Test
    void agentSeesClaimsAssignedToHimEvenOutsideHisServices() {
        Claim claim = claim(DELIVERY_SERVICE, agent.id());
        assertThat(ClaimAccessPolicy.canView(claim, agent, List.of(BILLING_SERVICE))).isTrue();
    }

    @Test
    void agentDoesNotSeeUnclassifiedClaims() {
        assertThat(ClaimAccessPolicy.canView(claim(null, null), agent, List.of(BILLING_SERVICE))).isFalse();
    }

    @Test
    void adminSeesEverything() {
        assertThat(ClaimAccessPolicy.canView(claim(null, null), admin, List.of())).isTrue();
        assertThat(ClaimAccessPolicy.canView(claim(DELIVERY_SERVICE, 99L), admin, List.of())).isTrue();
    }

    @Test
    void internalCommentsAreForStaffOnly() {
        assertThat(ClaimAccessPolicy.canSeeInternalComments(owner)).isFalse();
        assertThat(ClaimAccessPolicy.canSeeInternalComments(agent)).isTrue();
        assertThat(ClaimAccessPolicy.canSeeInternalComments(admin)).isTrue();
    }

    private static Claim claim(Long serviceId, Long agentId) {
        Claim claim = new Claim("Titre", "Description", OWNER, "owner@test");
        ProcessingService service = null;
        if (serviceId != null) {
            service = new ProcessingService("Service " + serviceId, null);
            ReflectionTestUtils.setField(service, "id", serviceId);
        }
        claim.assign(service, agentId, agentId == null ? null : "Agent");
        return claim;
    }
}

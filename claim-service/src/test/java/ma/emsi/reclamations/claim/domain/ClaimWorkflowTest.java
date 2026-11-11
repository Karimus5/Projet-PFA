package ma.emsi.reclamations.claim.domain;

import static ma.emsi.reclamations.claim.domain.ClaimStatus.ASSIGNED;
import static ma.emsi.reclamations.claim.domain.ClaimStatus.CLASSIFIED;
import static ma.emsi.reclamations.claim.domain.ClaimStatus.CLOSED;
import static ma.emsi.reclamations.claim.domain.ClaimStatus.IN_PROGRESS;
import static ma.emsi.reclamations.claim.domain.ClaimStatus.NEW;
import static ma.emsi.reclamations.claim.domain.ClaimStatus.REJECTED;
import static ma.emsi.reclamations.claim.domain.ClaimStatus.RESOLVED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumSet;
import java.util.Set;
import ma.emsi.reclamations.claim.error.ApiException;
import ma.emsi.reclamations.claim.security.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;

/** Règles du diagramme d'états (cahier des charges §8.4) et droits par rôle. */
class ClaimWorkflowTest {

    /** Les 7 seules transitions autorisées par le diagramme. */
    private static final Set<String> ALLOWED = Set.of(
            "NEW>CLASSIFIED", "NEW>REJECTED", "CLASSIFIED>ASSIGNED", "ASSIGNED>IN_PROGRESS",
            "IN_PROGRESS>RESOLVED", "RESOLVED>CLOSED", "RESOLVED>IN_PROGRESS");

    @Test
    void onlyTheDiagramTransitionsExist() {
        for (ClaimStatus from : ClaimStatus.values()) {
            for (ClaimStatus to : ClaimStatus.values()) {
                assertThat(from.canTransitionTo(to))
                        .as("%s → %s", from, to)
                        .isEqualTo(ALLOWED.contains(from + ">" + to));
            }
        }
    }

    @ParameterizedTest
    @EnumSource(value = ClaimStatus.class, names = {"CLOSED", "REJECTED"})
    void closedAndRejectedAreFinal(ClaimStatus status) {
        assertThat(EnumSet.allOf(ClaimStatus.class)).noneMatch(status::canTransitionTo);
        assertThat(status.isOpen()).isFalse();
    }

    @Test
    void forbiddenTransitionGives409EvenForAdmin() {
        assertThatThrownBy(() -> ClaimWorkflow.requireTransition(NEW, CLOSED, UserRole.ADMIN))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThatThrownBy(() -> ClaimWorkflow.requireTransition(CLOSED, IN_PROGRESS, UserRole.CLIENT))
                .extracting(e -> ((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThatThrownBy(() -> ClaimWorkflow.requireTransition(IN_PROGRESS, CLOSED, UserRole.CLIENT))
                .extracting(e -> ((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void onlyClientCanCloseOrReopenAResolvedClaim() {
        assertThatCode(() -> ClaimWorkflow.requireTransition(RESOLVED, CLOSED, UserRole.CLIENT)).doesNotThrowAnyException();
        assertThatCode(() -> ClaimWorkflow.requireTransition(RESOLVED, IN_PROGRESS, UserRole.CLIENT))
                .doesNotThrowAnyException();
        for (UserRole staff : new UserRole[] {UserRole.AGENT, UserRole.ADMIN}) {
            assertForbidden(RESOLVED, CLOSED, staff);
            assertForbidden(RESOLVED, IN_PROGRESS, staff);
        }
    }

    @Test
    void onlyStaffProcessesClaims() {
        for (UserRole staff : new UserRole[] {UserRole.AGENT, UserRole.ADMIN}) {
            assertThat(ClaimWorkflow.isAllowed(CLASSIFIED, ASSIGNED, staff)).isTrue();
            assertThat(ClaimWorkflow.isAllowed(ASSIGNED, IN_PROGRESS, staff)).isTrue();
            assertThat(ClaimWorkflow.isAllowed(IN_PROGRESS, RESOLVED, staff)).isTrue();
        }
        assertForbidden(ASSIGNED, IN_PROGRESS, UserRole.CLIENT);
        assertForbidden(IN_PROGRESS, RESOLVED, UserRole.CLIENT);
    }

    @Test
    void onlyAdminTriesNewClaims() {
        assertThat(ClaimWorkflow.isAllowed(NEW, CLASSIFIED, UserRole.ADMIN)).isTrue();
        assertThat(ClaimWorkflow.isAllowed(NEW, REJECTED, UserRole.ADMIN)).isTrue();
        assertForbidden(NEW, CLASSIFIED, UserRole.AGENT);
        assertForbidden(NEW, REJECTED, UserRole.AGENT);
        assertForbidden(NEW, REJECTED, UserRole.CLIENT);
    }

    private static void assertForbidden(ClaimStatus from, ClaimStatus to, UserRole role) {
        assertThatThrownBy(() -> ClaimWorkflow.requireTransition(from, to, role))
                .as("%s → %s par %s", from, to, role)
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}

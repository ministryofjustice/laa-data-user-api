package uk.gov.justice.laa.datauserapi.application.command.policy;

import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.CreateUserAccountCommand;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyDecision;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyDenialReason;

import static org.assertj.core.api.Assertions.assertThat;

class InternalActorCreationPolicyTest {
    private final InternalActorCreationPolicy policy = new InternalActorCreationPolicy();
    private final CreateUserAccountCommand command =
            new CreateUserAccountCommand("person@example.com", "First", "Last", "EXTERNAL", false, "firm-id");

    @Test
    void allowsActorWithActiveInternalProfile() {
        var actor = AuthenticatedEntraUser.builder()
                .active(true)
                .activeProfile(AuthenticatedUserProfile.builder()
                        .activeProfile(true).userType(UserType.INTERNAL).build())
                .build();

        assertThat(policy.evaluate(command, actor)).isEqualTo(PolicyDecision.allow());
    }

    @Test
    void deniesMissingOrIneligibleActorWithTypedReason() {
        var externalActor = AuthenticatedEntraUser.builder()
                .activeProfile(AuthenticatedUserProfile.builder()
                        .activeProfile(true).userType(UserType.EXTERNAL).build())
                .build();
        assertDenied(null);
        assertDenied(AuthenticatedEntraUser.builder().build());
        assertDenied(externalActor);
        assertDenied(AuthenticatedEntraUser.builder().active(false)
                .activeProfile(AuthenticatedUserProfile.builder()
                        .activeProfile(true).userType(UserType.INTERNAL).build())
                .build());
    }

    private void assertDenied(AuthenticatedEntraUser actor) {
        assertThat(policy.evaluate(command, actor))
                .isEqualTo(PolicyDecision.deny(PolicyDenialReason.ACTOR_NOT_ACTIVE_INTERNAL_USER));
    }
}

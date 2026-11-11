package uk.gov.justice.laa.datauserapi.application.command.policy;

import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.CreateUserAccountCommand;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyDenialReason;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class InternalActorOnlyPolicyTest {
    private final InternalActorOnlyPolicy policy = new InternalActorOnlyPolicy();

    @Test
    void nullActorReturnsStableDenialCode() {
        var decision = policy.evaluate(mock(CreateUserAccountCommand.class), null);

        assertThat(decision.granted()).isFalse();
        assertThat(decision.reason()).isEqualTo(PolicyDenialReason.ACTOR_NOT_ACTIVE_INTERNAL_USER);
    }

    @Test
    void nullCommandIsDeniedEvenForEligibleActor() {
        var profile = AuthenticatedUserProfile.builder().activeProfile(true).userType(UserType.INTERNAL)
                .build();
        var actor = AuthenticatedEntraUser.builder().active(true).activeProfile(profile).build();

        assertThat(policy.evaluate(mock(CreateUserAccountCommand.class), actor).granted()).isTrue();
        assertThat(policy.evaluate(null, actor).granted()).isFalse();
    }
}

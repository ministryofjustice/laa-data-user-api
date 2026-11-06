package uk.gov.justice.laa.datauserapi.application.command.policy;

import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.CreateUserAccountCommand;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedAppRole;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.model.Permission;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyDenialReason;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InternalUserCreationActorPolicyTest {
    private final InternalUserCreationActorPolicy policy = new InternalUserCreationActorPolicy();

    @Test
    void nullActorReturnsStableDenialCode() {
        var decision = policy.evaluate(mock(CreateUserAccountCommand.class), null);

        assertThat(decision.granted()).isFalse();
        assertThat(decision.reason()).isEqualTo(PolicyDenialReason.CREATE_EXTERNAL_USER_ACTOR_NOT_ELIGIBLE.name());
    }

    @Test
    void nullCommandIsDeniedEvenForEligibleActor() {
        var role = mock(AuthenticatedAppRole.class);
        when(role.authzRole()).thenReturn(true);
        when(role.permissions()).thenReturn(Set.of(Permission.CREATE_EXTERNAL_USER));
        var profile = AuthenticatedUserProfile.builder().activeProfile(true).userType(UserType.INTERNAL)
                .appRoles(Set.of(role)).build();
        var actor = AuthenticatedEntraUser.builder().active(true).activeProfile(profile).build();

        assertThat(policy.evaluate(mock(CreateUserAccountCommand.class), actor).granted()).isTrue();
        assertThat(policy.evaluate(null, actor).granted()).isFalse();
    }
}

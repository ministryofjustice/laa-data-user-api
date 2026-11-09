package uk.gov.justice.laa.datauserapi.application.query.policy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyDecision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActorProfileValidationPolicyTest {

    @InjectMocks
    private ActorProfileValidationPolicy policy;

    @Test
    void allowsActorWithActiveProfileAndValidUserType() {
        AuthenticatedEntraUser actor = mock(AuthenticatedEntraUser.class);
        AuthenticatedUserProfile profile = mock(AuthenticatedUserProfile.class);

        when(profile.userType()).thenReturn(UserType.INTERNAL);
        when(actor.activeProfile()).thenReturn(profile);

        PolicyDecision decision = policy.evaluate(new Object(), actor);

        assertThat(decision.granted()).isTrue();
    }

    @Test
    void deniesActorWithNoActiveProfile() {
        AuthenticatedEntraUser actor = mock(AuthenticatedEntraUser.class);
        when(actor.activeProfile()).thenReturn(null);

        PolicyDecision decision = policy.evaluate(new Object(), actor);

        assertThat(decision.granted()).isFalse();
    }

    @Test
    void deniesActorWithNullUserType() {
        AuthenticatedEntraUser actor = mock(AuthenticatedEntraUser.class);
        AuthenticatedUserProfile profile = mock(AuthenticatedUserProfile.class);

        when(profile.userType()).thenReturn(null);
        when(actor.activeProfile()).thenReturn(profile);

        PolicyDecision decision = policy.evaluate(new Object(), actor);

        assertThat(decision.granted()).isFalse();
    }
}

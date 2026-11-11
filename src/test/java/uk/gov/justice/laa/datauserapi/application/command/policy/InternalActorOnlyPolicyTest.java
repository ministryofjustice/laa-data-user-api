package uk.gov.justice.laa.datauserapi.application.command.policy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.convertmultifirm.ConvertToMultiFirmCommand;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyDecision;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyDenialReason;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InternalActorOnlyPolicyTest {

    private final InternalActorOnlyPolicy policy = new InternalActorOnlyPolicy();
    private final ConvertToMultiFirmCommand command = new ConvertToMultiFirmCommand(UUID.randomUUID());

    @Test
    void allowsActiveInternalProfile() {
        AuthenticatedEntraUser actor = AuthenticatedEntraUser.builder()
                .activeProfile(AuthenticatedUserProfile.builder()
                        .activeProfile(true).userType(UserType.INTERNAL).build()).build();

        assertThat(policy.evaluate(command, actor)).isEqualTo(PolicyDecision.allow());
    }

    @ParameterizedTest
    @ValueSource(strings = {"noActor", "noProfile", "inactive", "external", "unknownType"})
    void deniesIneligibleActorWithTypedReason(String restriction) {
        AuthenticatedEntraUser actor = restriction.equals("noActor") ? null : AuthenticatedEntraUser.builder()
                .activeProfile(restriction.equals("noProfile") ? null : AuthenticatedUserProfile.builder()
                        .activeProfile(!restriction.equals("inactive"))
                        .userType(restriction.equals("unknownType") ? null
                                : restriction.equals("external") ? UserType.EXTERNAL : UserType.INTERNAL)
                        .build()).build();

        assertThat(policy.evaluate(command, actor))
                .isEqualTo(PolicyDecision.deny(PolicyDenialReason.ACTOR_NOT_ACTIVE_INTERNAL_USER));
    }
}

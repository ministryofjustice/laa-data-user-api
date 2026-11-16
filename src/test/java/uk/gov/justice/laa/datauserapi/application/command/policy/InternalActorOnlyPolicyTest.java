package uk.gov.justice.laa.datauserapi.application.command.policy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.CreateUserAccountCommand;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.convertmultifirm.ConvertToMultiFirmCommand;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyDecision;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyDenialReason;

import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class InternalActorOnlyPolicyTest {

    private final InternalActorOnlyPolicy policy = new InternalActorOnlyPolicy();
    private final ConvertToMultiFirmCommand command = new ConvertToMultiFirmCommand(UUID.randomUUID());

    @ParameterizedTest
    @MethodSource("messages")
    void allowsActiveInternalActorRegardlessOfMessage(Object message) {
        AuthenticatedEntraUser actor = AuthenticatedEntraUser.builder()
                .active(true)
                .activeProfile(AuthenticatedUserProfile.builder()
                        .activeProfile(true).userType(UserType.INTERNAL).build()).build();

        assertThat(policy.evaluate(message, actor)).isEqualTo(PolicyDecision.allow());
    }

    @ParameterizedTest
    @ValueSource(strings = {"noActor", "noProfile", "inactiveAccount", "inactiveProfile", "external", "unknownType"})
    void deniesIneligibleActorWithTypedReason(String restriction) {
        AuthenticatedEntraUser actor = restriction.equals("noActor") ? null : AuthenticatedEntraUser.builder()
                .active(!restriction.equals("inactiveAccount"))
                .activeProfile(restriction.equals("noProfile") ? null : AuthenticatedUserProfile.builder()
                        .activeProfile(!restriction.equals("inactiveProfile"))
                        .userType(restriction.equals("unknownType") ? null
                                : restriction.equals("external") ? UserType.EXTERNAL : UserType.INTERNAL)
                        .build()).build();

        assertThat(policy.evaluate(command, actor))
                .isEqualTo(PolicyDecision.deny(PolicyDenialReason.ACTOR_NOT_ACTIVE_INTERNAL_USER));
    }

    private static Stream<Object> messages() {
        return Stream.of(
                new ConvertToMultiFirmCommand(UUID.randomUUID()),
                new CreateUserAccountCommand("person@example.com", "First", "Last", UserType.EXTERNAL, false, "firm-id"),
                new Object(),
                null);
    }
}

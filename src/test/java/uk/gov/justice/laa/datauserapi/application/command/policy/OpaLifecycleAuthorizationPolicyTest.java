package uk.gov.justice.laa.datauserapi.application.command.policy;

import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.datauserapi.application.command.dto.UserLifecycleAuthorizationContext;
import uk.gov.justice.laa.datauserapi.application.command.service.UserCommandService;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.DeactivateUserCommand;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.ReactivateUserCommand;
import uk.gov.justice.laa.datauserapi.contracts.domain.DeactivateUserReason;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedAppRole;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.model.AuthzRole;
import uk.gov.justice.laa.datauserapi.model.DeactivationType;
import uk.gov.justice.laa.datauserapi.model.Permission;
import uk.gov.justice.laa.datauserapi.security.opa.OpaAuthorizationClient;
import uk.gov.justice.laa.datauserapi.security.opa.OpaAuthorizationInput;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

class OpaLifecycleAuthorizationPolicyTest {

    private final UserCommandService userCommandService = mock(UserCommandService.class);
    private final OpaAuthorizationClient opaAuthorizationClient = mock(OpaAuthorizationClient.class);
    private final OpaLifecycleAuthorizationPolicy policy =
            new OpaLifecycleAuthorizationPolicy(userCommandService, opaAuthorizationClient);

    @Test
    void sendsLifecycleContextAndRolePermissionsToOpa() {
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UUID firmId = UUID.randomUUID();
        AuthenticatedAppRole authzRole = new AuthenticatedAppRole(
                UUID.randomUUID(), AuthzRole.EXTERNAL_USER_ADMIN.getRoleName(), 0, null,
                false, null, null, null, true, null, Set.of(Permission.DISABLE_EXTERNAL_USER));
        AuthenticatedAppRole nonAuthzRole = new AuthenticatedAppRole(
                UUID.randomUUID(), "Display-only role", 1, null,
                false, null, null, null, false, null, Set.of());
        AuthenticatedEntraUser actor = AuthenticatedEntraUser.builder()
                .entraOid(actorId.toString())
                .activeProfile(AuthenticatedUserProfile.builder()
                        .userType(UserType.INTERNAL)
                        .appRoles(Set.of(authzRole, nonAuthzRole))
                        .build())
                .build();
        DeactivateUserCommand command = DeactivateUserCommand.builder()
                .userEntraObjectId(targetId)
                .deactivateReason(DeactivateUserReason.Absence)
                .build();
        when(userCommandService.getLifecycleAuthorizationContext(targetId)).thenReturn(
                new UserLifecycleAuthorizationContext(
                        UserType.EXTERNAL, firmId, false, true, Optional.empty()));
        when(opaAuthorizationClient.isAllowed(any())).thenReturn(true);

        assertThat(policy.evaluate(command, actor).granted()).isTrue();

        verify(opaAuthorizationClient).isAllowed(new OpaAuthorizationInput(
                "deactivate",
                new OpaAuthorizationInput.Actor(
                        actorId.toString(), "INTERNAL", null,
                        Set.of(AuthzRole.EXTERNAL_USER_ADMIN.getRoleName()),
                        Set.of(Permission.DISABLE_EXTERNAL_USER.name())),
                new OpaAuthorizationInput.Target(
                        targetId.toString(), "EXTERNAL", firmId.toString(),
                        false, true, null)));
    }

    @Test
    void deniesWhenOpaDeniesTheDecision() {
        UUID targetId = UUID.randomUUID();
        AuthenticatedEntraUser actor = AuthenticatedEntraUser.builder()
                .entraOid(UUID.randomUUID().toString())
                .activeProfile(AuthenticatedUserProfile.builder()
                        .userType(UserType.INTERNAL)
                        .appRoles(Set.of())
                        .build())
                .build();
        when(userCommandService.getLifecycleAuthorizationContext(targetId)).thenReturn(
                new UserLifecycleAuthorizationContext(
                        UserType.EXTERNAL, UUID.randomUUID(), false, true, Optional.empty()));
        when(opaAuthorizationClient.isAllowed(any())).thenReturn(false);

        assertThat(policy.evaluate(
                DeactivateUserCommand.builder().userEntraObjectId(targetId).build(), actor).granted()).isFalse();
    }

    @Test
    void sendsReactivationStateToOpa() {
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UUID firmId = UUID.randomUUID();
        AuthenticatedAppRole role = new AuthenticatedAppRole(
                UUID.randomUUID(), AuthzRole.EXTERNAL_USER_MANAGER.getRoleName(), 0, null,
                false, null, null, null, true, null, Set.of(Permission.ENABLE_EXTERNAL_USER));
        AuthenticatedEntraUser actor = AuthenticatedEntraUser.builder()
                .entraOid(actorId.toString())
                .activeProfile(AuthenticatedUserProfile.builder()
                        .userType(UserType.INTERNAL)
                        .appRoles(Set.of(role))
                        .build())
                .build();
        when(userCommandService.getLifecycleAuthorizationContext(targetId)).thenReturn(
                new UserLifecycleAuthorizationContext(
                        UserType.EXTERNAL, firmId, false, false, Optional.of(DeactivationType.FIRM)));
        when(opaAuthorizationClient.isAllowed(any())).thenReturn(true);

        assertThat(policy.evaluate(
                ReactivateUserCommand.builder().userEntraObjectId(targetId).build(), actor).granted()).isTrue();

        verify(opaAuthorizationClient).isAllowed(new OpaAuthorizationInput(
                "reactivate",
                new OpaAuthorizationInput.Actor(
                        actorId.toString(), "INTERNAL", null,
                        Set.of(AuthzRole.EXTERNAL_USER_MANAGER.getRoleName()),
                        Set.of(Permission.ENABLE_EXTERNAL_USER.name())),
                new OpaAuthorizationInput.Target(
                        targetId.toString(), "EXTERNAL", firmId.toString(),
                        false, false, "FIRM")));
    }
}

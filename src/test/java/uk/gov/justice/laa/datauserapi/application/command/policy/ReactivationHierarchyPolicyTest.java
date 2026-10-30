package uk.gov.justice.laa.datauserapi.application.command.policy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.datauserapi.application.command.service.UserCommandService;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedAppRole;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.model.AuthzRole;
import uk.gov.justice.laa.datauserapi.model.DeactivationType;
import uk.gov.justice.laa.datauserapi.security.rbac.TargetsUser;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReactivationHierarchyPolicyTest {

    @Mock
    private UserCommandService userCommandService;

    private final UUID targetId = UUID.randomUUID();
    private final TargetsUser message = () -> targetId;

    @Test
    void deniesTargetThatIsNotDeactivated() {
        when(userCommandService.isDeactivated(targetId)).thenReturn(false);

        assertThat(policy().evaluate(message, actorWithRole(AuthzRole.GLOBAL_ADMIN)).granted()).isFalse();
    }

    @Test
    void deniesActorWithoutActiveProfile() {
        when(userCommandService.isDeactivated(targetId)).thenReturn(true);
        when(userCommandService.findDeactivationType(targetId)).thenReturn(Optional.of(DeactivationType.NONE));

        assertThat(policy().evaluate(message, AuthenticatedEntraUser.builder().build()).granted()).isTrue();
        when(userCommandService.findDeactivationType(targetId)).thenReturn(Optional.of(DeactivationType.LAA));
        assertThat(policy().evaluate(message, AuthenticatedEntraUser.builder().build()).granted()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
        "NONE,FIRM_USER_MANAGER,true",
        "NONE,INTERNAL_USER_MANAGER,true",
        "SYNC,FIRM_USER_MANAGER,false",
        "SYNC,EXTERNAL_USER_SUPPORT,true",
        "LAA,FIRM_USER_MANAGER,false",
        "LAA,EXTERNAL_USER_MANAGER,true",
        "FIRM,FIRM_USER_MANAGER,true",
        "FIRM,INTERNAL_USER_MANAGER,false",
        "PRIVILEGED,EXTERNAL_USER_ADMIN,false",
        "PRIVILEGED,EXTERNAL_USER_MANAGER,false",
        "PRIVILEGED,SECURITY_RESPONSE,true",
        "PRIVILEGED,GLOBAL_ADMIN,true",
        "SYNC,GLOBAL_ADMIN,true",
        "SYNC,SECURITY_RESPONSE,true",
        "SYNC,EXTERNAL_USER_ADMIN,true"
    })
    void appliesHierarchyByDeactivationType(DeactivationType type, AuthzRole role, boolean expected) {
        when(userCommandService.isDeactivated(targetId)).thenReturn(true);
        when(userCommandService.findDeactivationType(targetId)).thenReturn(Optional.of(type));

        assertThat(policy().evaluate(message, actorWithRole(role)).granted()).isEqualTo(expected);
    }

    @Test
    void ignoresNonAuthorizationRoleWithPrivilegedRoleName() {
        when(userCommandService.isDeactivated(targetId)).thenReturn(true);
        when(userCommandService.findDeactivationType(targetId)).thenReturn(Optional.of(DeactivationType.PRIVILEGED));
        AuthenticatedAppRole nonAuthorizationRole = new AuthenticatedAppRole(
                UUID.randomUUID(), AuthzRole.GLOBAL_ADMIN.getRoleName(), 0, null, false,
                null, null, "application role", false, null, Set.of());
        AuthenticatedEntraUser actor = AuthenticatedEntraUser.builder()
                .activeProfile(AuthenticatedUserProfile.builder().appRoles(Set.of(nonAuthorizationRole)).build())
                .build();

        assertThat(policy().evaluate(message, actor).granted()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
        "INTERNAL_USER_MANAGER,true",
        "EXTERNAL_USER_ADMIN,true",
        "EXTERNAL_USER_SUPPORT,true",
        "GLOBAL_ADMIN,true",
        "FIRM_USER_MANAGER,false",
        "INTERNAL_USER_VIEWER,false"
    })
    void legacyDeactivationsOnlyAllowInternalOrExternalManagement(AuthzRole role, boolean expected) {
        when(userCommandService.isDeactivated(targetId)).thenReturn(true);
        when(userCommandService.findDeactivationType(targetId)).thenReturn(Optional.empty());

        assertThat(policy().evaluate(message, actorWithRole(role)).granted()).isEqualTo(expected);
    }

    private ReactivationHierarchyPolicy policy() {
        return new ReactivationHierarchyPolicy(userCommandService);
    }

    private static AuthenticatedEntraUser actorWithRole(AuthzRole authzRole) {
        AuthenticatedAppRole role = new AuthenticatedAppRole(UUID.randomUUID(), authzRole.getRoleName(), 0, null, false,
                null, null, "desc", true, null, Set.of());
        return AuthenticatedEntraUser.builder()
                .entraOid(UUID.randomUUID().toString())
                .activeProfile(AuthenticatedUserProfile.builder().activeProfile(true).appRoles(Set.of(role)).build())
                .build();
    }
}

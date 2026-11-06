package uk.gov.justice.laa.datauserapi.application.query.policy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedAppRole;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.dto.UserProfileDto;
import uk.gov.justice.laa.datauserapi.model.Permission;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyDecision;
import uk.gov.justice.laa.datauserapi.security.rbac.TargetsUser;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TargetUserVisibilityPolicyTest {

    @Mock
    private TargetUserResolver resolver;

    private static final UUID TARGET_USER_ID = UUID.randomUUID();
    private static final UUID ACTOR_FIRM_ID = UUID.randomUUID();

    @Test
    void allowsInternalActorToViewInternalUserWithPermission() {
        TargetUserVisibilityPolicy policy = new TargetUserVisibilityPolicy(resolver);
        
        EntraUserDto targetUser = mockInternalUser();
        AuthenticatedEntraUser actor = mockInternalActorWithPermission(Permission.VIEW_INTERNAL_USER);
        TargetsUser message = mockTargetsUser(TARGET_USER_ID);

        when(resolver.resolveFor(message)).thenReturn(targetUser);

        PolicyDecision decision = policy.evaluate(message, actor);

        assertThat(decision.granted()).isTrue();
    }

    @Test
    void deniesInternalActorViewingInternalUserWithoutPermission() {
        TargetUserVisibilityPolicy policy = new TargetUserVisibilityPolicy(resolver);
        
        EntraUserDto targetUser = mockInternalUser();
        AuthenticatedEntraUser actor = mockInternalActorWithoutPermissions();
        TargetsUser message = mockTargetsUser(TARGET_USER_ID);

        when(resolver.resolveFor(message)).thenReturn(targetUser);

        PolicyDecision decision = policy.evaluate(message, actor);

        assertThat(decision.granted()).isFalse();
    }

    @Test
    void allowsInternalActorToViewExternalUserWithPermission() {
        TargetUserVisibilityPolicy policy = new TargetUserVisibilityPolicy(resolver);
        
        EntraUserDto targetUser = mockExternalUser();
        AuthenticatedEntraUser actor = mockInternalActorWithPermission(Permission.VIEW_EXTERNAL_USER);
        TargetsUser message = mockTargetsUser(TARGET_USER_ID);

        when(resolver.resolveFor(message)).thenReturn(targetUser);

        PolicyDecision decision = policy.evaluate(message, actor);

        assertThat(decision.granted()).isTrue();
    }

    @Test
    void allowsExternalActorToViewSameFirmUser() {
        TargetUserVisibilityPolicy policy = new TargetUserVisibilityPolicy(resolver);
        
        EntraUserDto targetUser = mockExternalUserWithFirm(ACTOR_FIRM_ID);
        AuthenticatedEntraUser actor = mockExternalActorWithFirm(ACTOR_FIRM_ID);
        TargetsUser message = mockTargetsUser(TARGET_USER_ID);

        when(resolver.resolveFor(message)).thenReturn(targetUser);

        PolicyDecision decision = policy.evaluate(message, actor);

        assertThat(decision.granted()).isTrue();
    }

    @Test
    void deniesExternalActorViewingDifferentFirmUser() {
        TargetUserVisibilityPolicy policy = new TargetUserVisibilityPolicy(resolver);
        
        UUID differentFirmId = UUID.randomUUID();
        EntraUserDto targetUser = mockExternalUserWithFirm(differentFirmId);
        AuthenticatedEntraUser actor = mockExternalActorWithFirm(ACTOR_FIRM_ID);
        TargetsUser message = mockTargetsUser(TARGET_USER_ID);

        when(resolver.resolveFor(message)).thenReturn(targetUser);

        PolicyDecision decision = policy.evaluate(message, actor);

        assertThat(decision.granted()).isFalse();
    }

    @Test
    void deniesWhenTargetUserNotFound() {
        TargetUserVisibilityPolicy policy = new TargetUserVisibilityPolicy(resolver);
        
        AuthenticatedEntraUser actor = mock(AuthenticatedEntraUser.class);
        TargetsUser message = mockTargetsUser(TARGET_USER_ID);

        when(resolver.resolveFor(message)).thenReturn(null);

        PolicyDecision decision = policy.evaluate(message, actor);

        assertThat(decision.granted()).isFalse();
    }

    private EntraUserDto mockInternalUser() {
        UserProfileDto profile = mock(UserProfileDto.class);
        when(profile.getUserType()).thenReturn(UserType.INTERNAL);
        when(profile.isActiveProfile()).thenReturn(true);

        EntraUserDto user = mock(EntraUserDto.class);
        when(user.getUserProfiles()).thenReturn(Set.of(profile));
        return user;
    }

    private EntraUserDto mockExternalUser() {
        UserProfileDto profile = mock(UserProfileDto.class);
        when(profile.getUserType()).thenReturn(UserType.EXTERNAL);
        when(profile.isActiveProfile()).thenReturn(true);

        EntraUserDto user = mock(EntraUserDto.class);
        when(user.getUserProfiles()).thenReturn(Set.of(profile));
        return user;
    }

    private EntraUserDto mockExternalUserWithFirm(UUID firmId) {
        UserProfileDto profile = mock(UserProfileDto.class);
        when(profile.getUserType()).thenReturn(UserType.EXTERNAL);
        when(profile.isActiveProfile()).thenReturn(true);
        
        var firm = mock(uk.gov.justice.laa.datauserapi.dto.FirmDto.class);
        when(firm.getId()).thenReturn(firmId);
        when(profile.getFirm()).thenReturn(firm);

        EntraUserDto user = mock(EntraUserDto.class);
        when(user.getUserProfiles()).thenReturn(Set.of(profile));
        return user;
    }

    private AuthenticatedEntraUser mockInternalActorWithPermission(Permission permission) {
        AuthenticatedEntraUser actor = mock(AuthenticatedEntraUser.class);
        AuthenticatedUserProfile profile = mock(AuthenticatedUserProfile.class);
        AuthenticatedAppRole role = mock(AuthenticatedAppRole.class);

        when(profile.userType()).thenReturn(UserType.INTERNAL);
        when(role.permissions()).thenReturn(Set.of(permission));
        when(profile.appRoles()).thenReturn(Set.of(role));
        when(actor.activeProfile()).thenReturn(profile);
        
        return actor;
    }

    private AuthenticatedEntraUser mockInternalActorWithoutPermissions() {
        AuthenticatedEntraUser actor = mock(AuthenticatedEntraUser.class);
        AuthenticatedUserProfile profile = mock(AuthenticatedUserProfile.class);
        AuthenticatedAppRole role = mock(AuthenticatedAppRole.class);

        when(profile.userType()).thenReturn(UserType.INTERNAL);
        when(role.permissions()).thenReturn(Set.of());
        when(profile.appRoles()).thenReturn(Set.of(role));
        when(actor.activeProfile()).thenReturn(profile);
        
        return actor;
    }

    private AuthenticatedEntraUser mockExternalActorWithFirm(UUID firmId) {
        AuthenticatedEntraUser actor = mock(AuthenticatedEntraUser.class);
        AuthenticatedUserProfile profile = mock(AuthenticatedUserProfile.class);

        when(profile.userType()).thenReturn(UserType.EXTERNAL);
        when(profile.firmId()).thenReturn(firmId);
        when(profile.appRoles()).thenReturn(Set.of());
        when(actor.activeProfile()).thenReturn(profile);
        
        return actor;
    }

    private TargetsUser mockTargetsUser(UUID userId) {
        return () -> userId;
    }
}

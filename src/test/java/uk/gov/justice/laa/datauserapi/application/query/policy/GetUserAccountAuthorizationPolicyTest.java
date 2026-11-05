package uk.gov.justice.laa.datauserapi.application.query.policy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.datauserapi.application.query.service.UserAccountQueryService;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedAppRole;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.dto.FirmDto;
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
class GetUserAccountAuthorizationPolicyTest {

    @Mock
    private UserAccountQueryService userAccountQueryService;

    @InjectMocks
    private GetUserAccountAuthorizationPolicy policy;

    private static final UUID TARGET_USER_ID = UUID.randomUUID();
    private static final UUID ACTOR_FIRM_ID = UUID.randomUUID();
    private static final UUID TARGET_FIRM_ID = UUID.randomUUID();

    @Test
    void allowsInternalActorToViewInternalUserWithPermission() {
        EntraUserDto targetUser = mockInternalUser();

        AuthenticatedEntraUser actor = mock(AuthenticatedEntraUser.class);
        AuthenticatedUserProfile profile = mock(AuthenticatedUserProfile.class);
        AuthenticatedAppRole role = mock(AuthenticatedAppRole.class);

        when(profile.userType()).thenReturn(UserType.INTERNAL);
        when(role.permissions()).thenReturn(Set.of(Permission.VIEW_INTERNAL_USER));
        when(profile.appRoles()).thenReturn(Set.of(role));
        when(actor.activeProfile()).thenReturn(profile);

        TargetsUser message = mockTargetsUser(TARGET_USER_ID);

        when(userAccountQueryService.findUserAccountSummaryByUserEntraObjectId(
                TARGET_USER_ID.toString()))
                .thenReturn(targetUser);

        PolicyDecision decision = policy.evaluate(message, actor);

        assertThat(decision.granted()).isTrue();
    }

    @Test
    void deniesInternalActorViewingInternalUserWithoutPermission() {
        EntraUserDto targetUser = mockInternalUser();

        AuthenticatedEntraUser actor = mock(AuthenticatedEntraUser.class);
        AuthenticatedUserProfile profile = mock(AuthenticatedUserProfile.class);

        when(profile.userType()).thenReturn(UserType.INTERNAL);
        when(profile.appRoles()).thenReturn(Set.of());
        when(actor.activeProfile()).thenReturn(profile);

        TargetsUser message = mockTargetsUser(TARGET_USER_ID);

        when(userAccountQueryService.findUserAccountSummaryByUserEntraObjectId(
                TARGET_USER_ID.toString()))
                .thenReturn(targetUser);

        PolicyDecision decision = policy.evaluate(message, actor);

        assertThat(decision.granted()).isFalse();
    }

    @Test
    void allowsExternalActorToViewUserFromSameFirm() {
        EntraUserDto targetUser = mockExternalUserWithFirm(TARGET_FIRM_ID);

        AuthenticatedEntraUser actor = mock(AuthenticatedEntraUser.class);
        AuthenticatedUserProfile profile = mock(AuthenticatedUserProfile.class);

        when(profile.userType()).thenReturn(UserType.EXTERNAL);
        when(profile.firmId()).thenReturn(TARGET_FIRM_ID);
        when(actor.activeProfile()).thenReturn(profile);

        TargetsUser message = mockTargetsUser(TARGET_USER_ID);

        when(userAccountQueryService.findUserAccountSummaryByUserEntraObjectId(
                TARGET_USER_ID.toString()))
                .thenReturn(targetUser);

        PolicyDecision decision = policy.evaluate(message, actor);

        assertThat(decision.granted()).isTrue();
    }

    @Test
    void deniesExternalActorViewingUserFromDifferentFirm() {
        EntraUserDto targetUser = mockExternalUserWithFirm(TARGET_FIRM_ID);

        AuthenticatedEntraUser actor = mock(AuthenticatedEntraUser.class);
        AuthenticatedUserProfile profile = mock(AuthenticatedUserProfile.class);

        when(profile.userType()).thenReturn(UserType.EXTERNAL);
        when(profile.firmId()).thenReturn(ACTOR_FIRM_ID);
        when(actor.activeProfile()).thenReturn(profile);

        TargetsUser message = mockTargetsUser(TARGET_USER_ID);

        when(userAccountQueryService.findUserAccountSummaryByUserEntraObjectId(
                TARGET_USER_ID.toString()))
                .thenReturn(targetUser);

        PolicyDecision decision = policy.evaluate(message, actor);

        assertThat(decision.granted()).isFalse();
    }

    @Test
    void allowsInternalActorWithPermissionToViewExternalUser() {
        EntraUserDto targetUser = mockExternalUserWithoutFirm();

        AuthenticatedEntraUser actor = mock(AuthenticatedEntraUser.class);
        AuthenticatedUserProfile profile = mock(AuthenticatedUserProfile.class);
        AuthenticatedAppRole role = mock(AuthenticatedAppRole.class);

        when(profile.userType()).thenReturn(UserType.INTERNAL);
        when(role.permissions()).thenReturn(Set.of(Permission.VIEW_EXTERNAL_USER));
        when(profile.appRoles()).thenReturn(Set.of(role));
        when(actor.activeProfile()).thenReturn(profile);

        TargetsUser message = mockTargetsUser(TARGET_USER_ID);

        when(userAccountQueryService.findUserAccountSummaryByUserEntraObjectId(
                TARGET_USER_ID.toString()))
                .thenReturn(targetUser);

        PolicyDecision decision = policy.evaluate(message, actor);

        assertThat(decision.granted()).isTrue();
    }

    @Test
    void deniesInternalActorWithoutPermissionToViewExternalUser() {
        EntraUserDto targetUser = mockExternalUserWithoutFirm();

        AuthenticatedEntraUser actor = mock(AuthenticatedEntraUser.class);
        AuthenticatedUserProfile profile = mock(AuthenticatedUserProfile.class);

        when(profile.userType()).thenReturn(UserType.INTERNAL);
        when(profile.appRoles()).thenReturn(Set.of());
        when(actor.activeProfile()).thenReturn(profile);

        TargetsUser message = mockTargetsUser(TARGET_USER_ID);

        when(userAccountQueryService.findUserAccountSummaryByUserEntraObjectId(
                TARGET_USER_ID.toString()))
                .thenReturn(targetUser);

        PolicyDecision decision = policy.evaluate(message, actor);

        assertThat(decision.granted()).isFalse();
    }

    @Test
    void allowsInternalActorWithPermissionToViewMultiFirmUser() {
        EntraUserDto targetUser = mockMultiFirmUser();

        AuthenticatedEntraUser actor = mock(AuthenticatedEntraUser.class);
        AuthenticatedUserProfile profile = mock(AuthenticatedUserProfile.class);
        AuthenticatedAppRole role = mock(AuthenticatedAppRole.class);

        when(profile.userType()).thenReturn(UserType.INTERNAL);
        when(role.permissions()).thenReturn(Set.of(Permission.VIEW_EXTERNAL_USER));
        when(profile.appRoles()).thenReturn(Set.of(role));
        when(actor.activeProfile()).thenReturn(profile);

        TargetsUser message = mockTargetsUser(TARGET_USER_ID);

        when(userAccountQueryService.findUserAccountSummaryByUserEntraObjectId(
                TARGET_USER_ID.toString()))
                .thenReturn(targetUser);

        PolicyDecision decision = policy.evaluate(message, actor);

        assertThat(decision.granted()).isTrue();
    }

    @Test
    void deniesExternalActorToViewMultiFirmUser() {
        EntraUserDto targetUser = mockMultiFirmUser();

        AuthenticatedEntraUser actor = mock(AuthenticatedEntraUser.class);
        AuthenticatedUserProfile profile = mock(AuthenticatedUserProfile.class);

        when(profile.userType()).thenReturn(UserType.EXTERNAL);
        when(actor.activeProfile()).thenReturn(profile);

        TargetsUser message = mockTargetsUser(TARGET_USER_ID);

        when(userAccountQueryService.findUserAccountSummaryByUserEntraObjectId(
                TARGET_USER_ID.toString()))
                .thenReturn(targetUser);

        PolicyDecision decision = policy.evaluate(message, actor);

        assertThat(decision.granted()).isFalse();
    }

    @Test
    void deniesExternalActorToViewInternalUser() {
        EntraUserDto targetUser = mockInternalUser();

        AuthenticatedEntraUser actor = mock(AuthenticatedEntraUser.class);
        AuthenticatedUserProfile profile = mock(AuthenticatedUserProfile.class);

        when(profile.userType()).thenReturn(UserType.EXTERNAL);
        when(actor.activeProfile()).thenReturn(profile);

        TargetsUser message = mockTargetsUser(TARGET_USER_ID);

        when(userAccountQueryService.findUserAccountSummaryByUserEntraObjectId(
                TARGET_USER_ID.toString()))
                .thenReturn(targetUser);

        PolicyDecision decision = policy.evaluate(message, actor);

        assertThat(decision.granted()).isFalse();
    }

    @Test
    void deniesActorWithNoActiveProfile() {
        EntraUserDto targetUser = mock(EntraUserDto.class);

        AuthenticatedEntraUser actor = mock(AuthenticatedEntraUser.class);
        when(actor.activeProfile()).thenReturn(null);

        TargetsUser message = mockTargetsUser(TARGET_USER_ID);

        when(userAccountQueryService.findUserAccountSummaryByUserEntraObjectId(
                TARGET_USER_ID.toString()))
                .thenReturn(targetUser);

        PolicyDecision decision = policy.evaluate(message, actor);

        assertThat(decision.granted()).isFalse();
    }


    
    private EntraUserDto mockInternalUser() {
        EntraUserDto user = mock(EntraUserDto.class);
        UserProfileDto profile = mock(UserProfileDto.class);
        when(profile.isActiveProfile()).thenReturn(true);
        when(profile.getUserType()).thenReturn(UserType.INTERNAL);
        when(user.getUserProfiles()).thenReturn(Set.of(profile));
        return user;
    }

    private EntraUserDto mockExternalUserWithFirm(UUID firmId) {
        EntraUserDto user = mock(EntraUserDto.class);
        UserProfileDto profile = mock(UserProfileDto.class);
        when(profile.isActiveProfile()).thenReturn(true);
        when(profile.getUserType()).thenReturn(UserType.EXTERNAL);
        FirmDto firm = mock(FirmDto.class);
        when(firm.getId()).thenReturn(firmId);
        when(profile.getFirm()).thenReturn(firm);
        when(user.getUserProfiles()).thenReturn(Set.of(profile));
        return user;
    }

    private EntraUserDto mockExternalUserWithoutFirm() {
        EntraUserDto user = mock(EntraUserDto.class);
        UserProfileDto profile = mock(UserProfileDto.class);
        when(profile.isActiveProfile()).thenReturn(true);
        when(profile.getUserType()).thenReturn(UserType.EXTERNAL);
        when(user.getUserProfiles()).thenReturn(Set.of(profile));
        return user;
    }


    private EntraUserDto mockMultiFirmUser() {
        EntraUserDto user = mock(EntraUserDto.class);
        when(user.isMultiFirmUser()).thenReturn(true);
        return user;
    }


    private TargetsUser mockTargetsUser(UUID userId) {
        TargetsUser message = mock(TargetsUser.class);
        when(message.targetUserEntraObjectId()).thenReturn(userId);
        return message;
    }
}

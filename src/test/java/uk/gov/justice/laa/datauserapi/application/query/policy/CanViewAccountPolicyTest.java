package uk.gov.justice.laa.datauserapi.application.query.policy;

import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.datauserapi.application.query.dto.UserAccountVisibilityContext;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedAppRole;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.model.Permission;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyDecision;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyDenialReason;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CanViewAccountPolicyTest {

    private final CanViewAccountPolicy policy = new CanViewAccountPolicy();

    @Test
    void allowsInternalActorToViewInternalUser() {
        AuthenticatedEntraUser actor = internalActor();
        UserAccountVisibilityContext context = new UserAccountVisibilityContext(true, true, false, null);

        PolicyDecision decision = policy.evaluate(context, actor);

        assertThat(decision.granted()).isTrue();
    }

    @Test
    void forbidsInternalActorToViewExternalUserWithoutPermission() {
        AuthenticatedEntraUser actor = internalActor();
        UserAccountVisibilityContext context = new UserAccountVisibilityContext(true, false, true, UUID.randomUUID());

        PolicyDecision decision = policy.evaluate(context, actor);

        assertThat(decision.granted()).isFalse();
        assertThat(decision.reason()).isEqualTo(PolicyDenialReason.TARGET_PROFILE_NOT_VISIBLE);
    }

    @Test
    void allowsInternalActorWithPermissionToViewExternalUser() {
        AuthenticatedEntraUser actor = internalActorWith(Permission.VIEW_EXTERNAL_USER);
        UserAccountVisibilityContext context = new UserAccountVisibilityContext(true, false, true, UUID.randomUUID());

        PolicyDecision decision = policy.evaluate(context, actor);

        assertThat(decision.granted()).isTrue();
    }

    @Test
    void allowsExternalActorToViewOwnAccount() {
        AuthenticatedEntraUser actor = externalActor(UUID.randomUUID());
        UserAccountVisibilityContext context = new UserAccountVisibilityContext(true, false, true, null);

        PolicyDecision decision = policy.evaluate(context, actor);

        assertThat(decision.granted()).isTrue();
    }

    @Test
    void allowsExternalActorToViewUserFromSameFirm() {
        UUID firmId = UUID.randomUUID();
        AuthenticatedEntraUser actor = externalActorWith(firmId, Permission.VIEW_EXTERNAL_USER);
        UserAccountVisibilityContext context = new UserAccountVisibilityContext(true, false, true, firmId);

        PolicyDecision decision = policy.evaluate(context, actor);

        assertThat(decision.granted()).isTrue();
    }

    @Test
    void forbidsExternalActorViewingUserFromDifferentFirm() {
        UUID actorFirmId = UUID.randomUUID();
        UUID targetFirmId = UUID.randomUUID();
        AuthenticatedEntraUser actor = externalActorWith(actorFirmId, Permission.VIEW_EXTERNAL_USER);
        // Target user has both internal and external profiles, so not "own account"
        UserAccountVisibilityContext context = new UserAccountVisibilityContext(true, true, true, targetFirmId);

        PolicyDecision decision = policy.evaluate(context, actor);

        assertThat(decision.granted()).isFalse();
        assertThat(decision.reason()).isEqualTo(PolicyDenialReason.TARGET_PROFILE_NOT_VISIBLE);
    }

    @Test
    void deniesAccessToMissingUser() {
        AuthenticatedEntraUser actor = internalActor();
        UserAccountVisibilityContext context = UserAccountVisibilityContext.missing();

        PolicyDecision decision = policy.evaluate(context, actor);

        assertThat(decision.granted()).isFalse();
        assertThat(decision.reason()).isEqualTo(PolicyDenialReason.TARGET_PROFILE_NOT_FOUND);
    }

    @Test
    void deniesAccessWhenActorHasNoActiveProfile() {
        AuthenticatedEntraUser actor = mock(AuthenticatedEntraUser.class);
        when(actor.activeProfile()).thenReturn(null);
        UserAccountVisibilityContext context = new UserAccountVisibilityContext(true, true, false, null);

        PolicyDecision decision = policy.evaluate(context, actor);

        assertThat(decision.granted()).isFalse();
        assertThat(decision.reason()).isEqualTo(PolicyDenialReason.ACTOR_NO_ACTIVE_PROFILE);
    }

    @Test
    void deniesAccessWhenActorUserTypeIsNull() {
        AuthenticatedUserProfile profile = mock(AuthenticatedUserProfile.class);
        when(profile.userType()).thenReturn(null);
        AuthenticatedEntraUser actor = mock(AuthenticatedEntraUser.class);
        when(actor.activeProfile()).thenReturn(profile);
        UserAccountVisibilityContext context = new UserAccountVisibilityContext(true, true, false, null);

        PolicyDecision decision = policy.evaluate(context, actor);

        assertThat(decision.granted()).isFalse();
        assertThat(decision.reason()).isEqualTo(PolicyDenialReason.ACTOR_NO_ACTIVE_PROFILE);
    }

    private AuthenticatedEntraUser internalActor() {
        return internalActorWith();
    }

    private AuthenticatedEntraUser internalActorWith(Permission... permissions) {
        return actor(UserType.INTERNAL, null, permissions);
    }

    private AuthenticatedEntraUser externalActor(UUID firmId) {
        return externalActorWith(firmId);
    }

    private AuthenticatedEntraUser externalActorWith(UUID firmId, Permission... permissions) {
        return actor(UserType.EXTERNAL, firmId, permissions);
    }

    private AuthenticatedEntraUser actor(UserType userType, UUID firmId, Permission... permissions) {
        AuthenticatedAppRole role = new AuthenticatedAppRole(UUID.randomUUID(), "role", 0, null, false,
                null, null, "desc", true, null, Set.copyOf(Arrays.asList(permissions)));
        AuthenticatedUserProfile profile = AuthenticatedUserProfile.builder()
                .id(UUID.randomUUID())
                .activeProfile(true)
                .userType(userType)
                .firmId(firmId)
                .appRoles(Set.of(role))
                .build();
        return AuthenticatedEntraUser.builder()
                .active(true)
                .id(UUID.randomUUID())
                .entraOid(UUID.randomUUID().toString())
                .userProfiles(Set.of(profile))
                .activeProfile(profile)
                .build();
    }
}

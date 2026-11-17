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
        UserAccountVisibilityContext context = new UserAccountVisibilityContext(true, true, false, Set.of(), UUID.randomUUID().toString());

        PolicyDecision decision = policy.evaluate(context, actor);

        assertThat(decision.granted()).isTrue();
    }

    @Test
    void forbidsInternalActorToViewExternalUserWithoutPermission() {
        UUID firmId = UUID.randomUUID();
        AuthenticatedEntraUser actor = internalActor();
        UserAccountVisibilityContext context = new UserAccountVisibilityContext(true, false, true, Set.of(firmId), UUID.randomUUID().toString());

        PolicyDecision decision = policy.evaluate(context, actor);

        assertThat(decision.granted()).isFalse();
        assertThat(decision.reason()).isEqualTo(PolicyDenialReason.TARGET_PROFILE_NOT_VISIBLE);
    }

    @Test
    void allowsInternalActorWithPermissionToViewExternalUser() {
        UUID firmId = UUID.randomUUID();
        AuthenticatedEntraUser actor = internalActorWith(Permission.VIEW_EXTERNAL_USER);
        UserAccountVisibilityContext context = new UserAccountVisibilityContext(true, false, true, Set.of(firmId), UUID.randomUUID().toString());

        PolicyDecision decision = policy.evaluate(context, actor);

        assertThat(decision.granted()).isTrue();
    }

    @Test
    void allowsExternalActorToViewOwnAccount() {
        String actorOid = UUID.randomUUID().toString();
        AuthenticatedEntraUser actor = externalActor(UUID.randomUUID(), actorOid);
        UserAccountVisibilityContext context = new UserAccountVisibilityContext(true, false, true, Set.of(), actorOid);

        PolicyDecision decision = policy.evaluate(context, actor);

        assertThat(decision.granted()).isTrue();
    }

    @Test
    void forbidsExternalActorToViewAnotherExternalAccount() {
        String actorOid = UUID.randomUUID().toString();
        String targetOid = UUID.randomUUID().toString();
        AuthenticatedEntraUser actor = externalActor(UUID.randomUUID(), actorOid);
        UserAccountVisibilityContext context = new UserAccountVisibilityContext(true, false, true, Set.of(), targetOid);

        PolicyDecision decision = policy.evaluate(context, actor);

        assertThat(decision.granted()).isFalse();
        assertThat(decision.reason()).isEqualTo(PolicyDenialReason.TARGET_PROFILE_NOT_VISIBLE);
    }

    @Test
    void allowsExternalActorToViewUserFromSameFirm() {
        UUID firmId = UUID.randomUUID();
        AuthenticatedEntraUser actor = externalActorWith(firmId, Permission.VIEW_EXTERNAL_USER);
        UserAccountVisibilityContext context = new UserAccountVisibilityContext(true, false, true, Set.of(firmId), UUID.randomUUID().toString());

        PolicyDecision decision = policy.evaluate(context, actor);

        assertThat(decision.granted()).isTrue();
    }

    @Test
    void forbidsExternalActorViewingUserFromDifferentFirm() {
        UUID actorFirmId = UUID.randomUUID();
        UUID targetFirmId = UUID.randomUUID();
        AuthenticatedEntraUser actor = externalActorWith(actorFirmId, Permission.VIEW_EXTERNAL_USER);
        // Target user has both internal and external profiles, so not "own account"
        UserAccountVisibilityContext context = new UserAccountVisibilityContext(true, true, true, Set.of(targetFirmId), UUID.randomUUID().toString());

        PolicyDecision decision = policy.evaluate(context, actor);

        assertThat(decision.granted()).isFalse();
        assertThat(decision.reason()).isEqualTo(PolicyDenialReason.TARGET_PROFILE_NOT_VISIBLE);
    }

    @Test
    void allowsExternalActorToViewUserWhenTargetHasMultipleFirmsIncludingActorFirm() {
        UUID actorFirmId = UUID.randomUUID();
        UUID otherFirmId = UUID.randomUUID();
        AuthenticatedEntraUser actor = externalActorWith(actorFirmId, Permission.VIEW_EXTERNAL_USER);
        UserAccountVisibilityContext context = new UserAccountVisibilityContext(true, false, true, Set.of(otherFirmId, actorFirmId), UUID.randomUUID().toString());

        PolicyDecision decision = policy.evaluate(context, actor);

        assertThat(decision.granted()).isTrue();
    }

    @Test
    void forbidsExternalActorWhenTargetHasMultipleFirmsButNotActorFirm() {
        UUID actorFirmId = UUID.randomUUID();
        UUID firmA = UUID.randomUUID();
        UUID firmB = UUID.randomUUID();
        AuthenticatedEntraUser actor = externalActorWith(actorFirmId, Permission.VIEW_EXTERNAL_USER);
        UserAccountVisibilityContext context = new UserAccountVisibilityContext(true, false, true, Set.of(firmA, firmB), UUID.randomUUID().toString());

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
        UserAccountVisibilityContext context = new UserAccountVisibilityContext(true, true, false, Set.of(), UUID.randomUUID().toString());

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
        UserAccountVisibilityContext context = new UserAccountVisibilityContext(true, true, false, Set.of(), UUID.randomUUID().toString());

        PolicyDecision decision = policy.evaluate(context, actor);

        assertThat(decision.granted()).isFalse();
        assertThat(decision.reason()).isEqualTo(PolicyDenialReason.ACTOR_NO_ACTIVE_PROFILE);
    }

    private AuthenticatedEntraUser externalActor(UUID firmId) {
        return externalActor(firmId, UUID.randomUUID().toString());
    }

    private AuthenticatedEntraUser externalActor(UUID firmId, String oid) {
        return externalActorWith(firmId, oid);
    }

    private AuthenticatedEntraUser externalActorWith(UUID firmId, Permission... permissions) {
        return externalActorWith(firmId, UUID.randomUUID().toString(), permissions);
    }

    private AuthenticatedEntraUser externalActorWith(UUID firmId, String oid, Permission... permissions) {
        return actor(UserType.EXTERNAL, firmId, oid, permissions);
    }

    private AuthenticatedEntraUser internalActor() {
        return internalActorWith();
    }

    private AuthenticatedEntraUser internalActorWith(Permission... permissions) {
        return actor(UserType.INTERNAL, null, UUID.randomUUID().toString(), permissions);
    }

    private AuthenticatedEntraUser actor(UserType userType, UUID firmId, String oid, Permission... permissions) {
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
                .entraOid(oid)
                .userProfiles(Set.of(profile))
                .activeProfile(profile)
                .build();
    }
}

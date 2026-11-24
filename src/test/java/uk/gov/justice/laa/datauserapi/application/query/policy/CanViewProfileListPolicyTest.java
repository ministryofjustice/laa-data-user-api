package uk.gov.justice.laa.datauserapi.application.query.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import uk.gov.justice.laa.datauserapi.application.query.dto.ProfileVisibilityContext;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedAppRole;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.model.Permission;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyDecision;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyDenialReason;

class CanViewProfileListPolicyTest {

    private static final UUID FIRM_ID = UUID.randomUUID();

    private final CanViewProfileListPolicy policy = new CanViewProfileListPolicy();

    @Test
    void allowsWhenAtLeastOneProfileIsVisible() {
        List<ProfileVisibilityContext> contexts = List.of(
                profile(UserType.EXTERNAL, UUID.randomUUID()),
                profile(UserType.EXTERNAL, FIRM_ID));

        assertGranted(policy.evaluate(contexts, external(FIRM_ID, Permission.VIEW_EXTERNAL_USER)));
    }

    @Test
    void deniesWhenNoProfileInTheListIsVisible() {
        List<ProfileVisibilityContext> contexts = List.of(
                profile(UserType.EXTERNAL, UUID.randomUUID()),
                profile(UserType.INTERNAL, null));

        assertDenied(policy.evaluate(contexts, external(FIRM_ID)), PolicyDenialReason.TARGET_PROFILE_NOT_VISIBLE);
    }

    @Test
    void deniesWhenTheListIsEmpty() {
        assertDenied(policy.evaluate(List.of(), external(FIRM_ID, Permission.VIEW_EXTERNAL_USER)),
                PolicyDenialReason.TARGET_PROFILE_NOT_FOUND);
    }

    @Test
    void ignoresNonExistentProfilesWhenCheckingVisibility() {
        List<ProfileVisibilityContext> contexts = List.of(ProfileVisibilityContext.missing());

        assertDenied(policy.evaluate(contexts, external(FIRM_ID, Permission.VIEW_EXTERNAL_USER)),
                PolicyDenialReason.TARGET_PROFILE_NOT_VISIBLE);
    }

    @Test
    void externalActorMayAlwaysViewTheirOwnProfileEvenWithoutViewExternalPermission() {
        AuthenticatedEntraUser actor = external(FIRM_ID);
        ProfileVisibilityContext ownProfile =
                new ProfileVisibilityContext(true, actor.activeProfile().id(), UserType.EXTERNAL, FIRM_ID);

        assertGranted(policy.evaluate(List.of(ownProfile), actor));
    }

    @Test
    void actorWithoutActiveProfileFailsClosed() {
        AuthenticatedEntraUser noProfile = AuthenticatedEntraUser.builder().entraOid("oid").build();

        assertDenied(policy.evaluate(List.of(profile(UserType.EXTERNAL, FIRM_ID)), noProfile),
                PolicyDenialReason.ACTOR_NO_ACTIVE_PROFILE);
    }

    @Test
    void internalActorWithViewInternalPermissionMayViewInternalProfilesInTheList() {
        List<ProfileVisibilityContext> contexts = List.of(profile(UserType.INTERNAL, null));

        assertGranted(policy.evaluate(contexts, internal(Permission.VIEW_INTERNAL_USER)));
    }

    private static ProfileVisibilityContext profile(UserType type, UUID firmId) {
        return new ProfileVisibilityContext(true, UUID.randomUUID(), type, firmId);
    }

    private static AuthenticatedEntraUser internal(Permission permission) {
        AuthenticatedAppRole role = new AuthenticatedAppRole(UUID.randomUUID(), "Role", 1, "ROLE", false, null, null,
                "Role", true, null, Set.of(permission));
        return actor(AuthenticatedUserProfile.builder().id(UUID.randomUUID()).activeProfile(true)
                .userType(UserType.INTERNAL).appRoles(Set.of(role)).build());
    }

    private static AuthenticatedEntraUser external(UUID firmId, Permission... permissions) {
        Set<AuthenticatedAppRole> roles = permissions.length == 0 ? Set.of()
                : Set.of(new AuthenticatedAppRole(UUID.randomUUID(), "Role", 1, "ROLE", false, null, null, "Role",
                        true, null, Set.of(permissions)));
        return actor(AuthenticatedUserProfile.builder().id(UUID.randomUUID()).activeProfile(true)
                .userType(UserType.EXTERNAL).firmId(firmId).appRoles(roles).build());
    }

    private static AuthenticatedEntraUser actor(AuthenticatedUserProfile profile) {
        return AuthenticatedEntraUser.builder().entraOid(UUID.randomUUID().toString()).activeProfile(profile).build();
    }

    private static void assertGranted(PolicyDecision decision) {
        assertThat(decision.granted()).isTrue();
    }

    private static void assertDenied(PolicyDecision decision, PolicyDenialReason reason) {
        assertThat(decision.granted()).isFalse();
        assertThat(decision.reason()).isEqualTo(reason);
    }
}

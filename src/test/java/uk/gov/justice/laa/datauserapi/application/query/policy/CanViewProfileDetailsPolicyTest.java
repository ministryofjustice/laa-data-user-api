package uk.gov.justice.laa.datauserapi.application.query.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import uk.gov.justice.laa.datauserapi.application.query.dto.ProfileVisibilityContext;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedAppRole;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.model.Permission;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyDecision;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyDenialReason;

class CanViewProfileDetailsPolicyTest {

    private static final UUID FIRM_ID = UUID.randomUUID();

    private final CanViewProfileDetailsPolicy policy = new CanViewProfileDetailsPolicy();

    @Test
    void internalActorWithViewInternalPermissionMayViewInternalProfile() {
        assertGranted(policy.evaluate(profile(UserType.INTERNAL, null), internal(Permission.VIEW_INTERNAL_USER)));
    }

    @Test
    void internalActorWithViewExternalPermissionMayViewExternalProfile() {
        assertGranted(policy.evaluate(profile(UserType.EXTERNAL, FIRM_ID), internal(Permission.VIEW_EXTERNAL_USER)));
    }

    @Test
    void externalActorWithViewExternalPermissionMayViewProfileInOwnFirm() {
        assertGranted(policy.evaluate(profile(UserType.EXTERNAL, FIRM_ID),
                external(FIRM_ID, Permission.VIEW_EXTERNAL_USER)));
    }

    @Test
    void externalActorWithoutViewExternalPermissionMayNotViewColleagueInOwnFirm() {
        assertDenied(policy.evaluate(profile(UserType.EXTERNAL, FIRM_ID), external(FIRM_ID)),
                PolicyDenialReason.TARGET_PROFILE_NOT_VISIBLE);
    }

    @Test
    void externalActorMayViewTheirOwnProfileWithoutViewExternalPermission() {
        AuthenticatedEntraUser actor = external(FIRM_ID);
        ProfileVisibilityContext ownProfile =
                new ProfileVisibilityContext(true, actor.activeProfile().id(), UserType.EXTERNAL, FIRM_ID);

        assertGranted(policy.evaluate(ownProfile, actor));
    }

    @Test
    void externalActorWithViewExternalPermissionMayNotViewProfileInAnotherFirm() {
        assertDenied(policy.evaluate(profile(UserType.EXTERNAL, FIRM_ID),
                        external(UUID.randomUUID(), Permission.VIEW_EXTERNAL_USER)),
                PolicyDenialReason.TARGET_PROFILE_NOT_VISIBLE);
    }

    @Test
    void externalActorMayNotViewInternalProfileEvenWithViewExternalPermission() {
        assertDenied(policy.evaluate(profile(UserType.INTERNAL, null),
                        external(FIRM_ID, Permission.VIEW_EXTERNAL_USER)),
                PolicyDenialReason.TARGET_PROFILE_NOT_VISIBLE);
    }

    @Test
    void internalActorWithoutTheMatchingPermissionIsDenied() {
        assertDenied(policy.evaluate(profile(UserType.EXTERNAL, FIRM_ID), internal(Permission.VIEW_INTERNAL_USER)),
                PolicyDenialReason.TARGET_PROFILE_NOT_VISIBLE);
        assertDenied(policy.evaluate(profile(UserType.INTERNAL, null), internal(Permission.VIEW_EXTERNAL_USER)),
                PolicyDenialReason.TARGET_PROFILE_NOT_VISIBLE);
    }

    @Test
    void missingProfileIsDeniedEvenForAnActorWhoCouldViewOthers() {
        assertDenied(policy.evaluate(ProfileVisibilityContext.missing(), internal(Permission.VIEW_EXTERNAL_USER)),
                PolicyDenialReason.TARGET_PROFILE_NOT_FOUND);
    }

    @Test
    void actorWithoutActiveProfileFailsClosed() {
        AuthenticatedEntraUser noProfile = AuthenticatedEntraUser.builder().entraOid("oid").build();

        assertDenied(policy.evaluate(profile(UserType.EXTERNAL, FIRM_ID), noProfile),
                PolicyDenialReason.ACTOR_NO_ACTIVE_PROFILE);
    }

    @ParameterizedTest
    @MethodSource("rolesWithoutPermissions")
    void missingRoleDataDeniesInternalActorWithoutThrowing(Set<AuthenticatedAppRole> roles) {
        AuthenticatedEntraUser actor = actor(AuthenticatedUserProfile.builder()
                .userType(UserType.INTERNAL).appRoles(roles).build());

        assertDenied(policy.evaluate(profile(UserType.INTERNAL, null), actor),
                PolicyDenialReason.TARGET_PROFILE_NOT_VISIBLE);
        assertDenied(policy.evaluate(profile(UserType.EXTERNAL, FIRM_ID), actor),
                PolicyDenialReason.TARGET_PROFILE_NOT_VISIBLE);
    }

    private static Stream<Set<AuthenticatedAppRole>> rolesWithoutPermissions() {
        return Stream.of(null, Collections.singleton(null),
                Set.of(new AuthenticatedAppRole(UUID.randomUUID(), "Role", 1, "ROLE", false, null, null,
                        "Role", true, null, null)));
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

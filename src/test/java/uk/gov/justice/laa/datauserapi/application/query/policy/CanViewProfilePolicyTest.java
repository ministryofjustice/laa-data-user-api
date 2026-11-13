package uk.gov.justice.laa.datauserapi.application.query.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import uk.gov.justice.laa.datauserapi.application.query.dto.ProfileVisibilityContext;
import uk.gov.justice.laa.datauserapi.application.query.service.QueryAuthorisationService;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedAppRole;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.model.Permission;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyDecision;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyDenialReason;

class CanViewProfilePolicyTest {

    private static final UUID FIRM_ID = UUID.randomUUID();

    private final CanViewProfilePolicy policy = new CanViewProfilePolicy();

    @Test
    void internalActorWithViewInternalPermissionMayViewInternalProfile() {
        assertGranted(policy.evaluate(profile(UserType.INTERNAL, null), internal(Permission.VIEW_INTERNAL_USER)));
    }

    @Test
    void internalActorWithViewExternalPermissionMayViewExternalProfile() {
        assertGranted(policy.evaluate(profile(UserType.EXTERNAL, FIRM_ID), internal(Permission.VIEW_EXTERNAL_USER)));
    }

    @Test
    void externalActorMayViewProfileInOwnFirm() {
        assertGranted(policy.evaluate(profile(UserType.EXTERNAL, FIRM_ID), external(FIRM_ID)));
    }

    @Test
    void externalActorMayNotViewProfileInAnotherFirm() {
        assertDenied(policy.evaluate(profile(UserType.EXTERNAL, FIRM_ID), external(UUID.randomUUID())),
                PolicyDenialReason.TARGET_PROFILE_NOT_VISIBLE);
    }

    @Test
    void externalActorMayNotViewInternalProfile() {
        assertDenied(policy.evaluate(profile(UserType.INTERNAL, null), external(FIRM_ID)),
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

    @Test
    void agreesWithTheProfileDetailVisibilityRuleForEveryActorAndTarget() {
        QueryAuthorisationService detailRule = new QueryAuthorisationService();
        UUID otherFirm = UUID.randomUUID();
        List<AuthenticatedEntraUser> actors = List.of(
                internalWith(), internalWith(Permission.VIEW_INTERNAL_USER), internalWith(Permission.VIEW_EXTERNAL_USER),
                internalWith(Permission.VIEW_INTERNAL_USER, Permission.VIEW_EXTERNAL_USER),
                external(FIRM_ID), external(otherFirm), external(null));
        List<ProfileVisibilityContext> targets = List.of(
                profile(UserType.INTERNAL, null), profile(UserType.EXTERNAL, FIRM_ID),
                profile(UserType.EXTERNAL, otherFirm), profile(UserType.EXTERNAL, null));

        for (AuthenticatedEntraUser actor : actors) {
            for (ProfileVisibilityContext target : targets) {
                String firmId = target.firmId() == null ? null : target.firmId().toString();
                boolean expected = detailRule.canViewProfile(actor.activeProfile(), target.userType(), firmId);

                assertThat(policy.evaluate(target, actor).granted())
                        .as("actor %s, target %s", actor.activeProfile(), target)
                        .isEqualTo(expected);
            }
        }
    }

    private static AuthenticatedEntraUser internalWith(Permission... permissions) {
        Set<AuthenticatedAppRole> roles = permissions.length == 0 ? Set.of()
                : Set.of(new AuthenticatedAppRole(UUID.randomUUID(), "Role", 1, "ROLE", false, null, null, "Role",
                        true, null, Set.of(permissions)));
        return actor(AuthenticatedUserProfile.builder().id(UUID.randomUUID()).activeProfile(true)
                .userType(UserType.INTERNAL).appRoles(roles).build());
    }

    private static ProfileVisibilityContext profile(UserType type, UUID firmId) {
        return new ProfileVisibilityContext(true, type, firmId);
    }

    private static AuthenticatedEntraUser internal(Permission permission) {
        AuthenticatedAppRole role = new AuthenticatedAppRole(UUID.randomUUID(), "Role", 1, "ROLE", false, null, null,
                "Role", true, null, Set.of(permission));
        return actor(AuthenticatedUserProfile.builder().id(UUID.randomUUID()).activeProfile(true)
                .userType(UserType.INTERNAL).appRoles(Set.of(role)).build());
    }

    private static AuthenticatedEntraUser external(UUID firmId) {
        return actor(AuthenticatedUserProfile.builder().id(UUID.randomUUID()).activeProfile(true)
                .userType(UserType.EXTERNAL).firmId(firmId).appRoles(Set.of()).build());
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

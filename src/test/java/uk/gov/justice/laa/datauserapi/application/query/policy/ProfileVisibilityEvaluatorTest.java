package uk.gov.justice.laa.datauserapi.application.query.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import uk.gov.justice.laa.datauserapi.application.query.dto.ProfileVisibilityContext;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedAppRole;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.model.Permission;

class ProfileVisibilityEvaluatorTest {

    private static final UUID FIRM_ID = UUID.randomUUID();

    @Test
    void internalActorWithViewInternalPermissionMayViewInternalProfile() {
        assertThat(ProfileVisibilityEvaluator.isProfileVisible(
                internal(Permission.VIEW_INTERNAL_USER), profile(UserType.INTERNAL, null))).isTrue();
    }

    @Test
    void internalActorWithoutViewInternalPermissionMayNotViewInternalProfile() {
        assertThat(ProfileVisibilityEvaluator.isProfileVisible(
                internal(), profile(UserType.INTERNAL, null))).isFalse();
    }

    @Test
    void internalActorWithViewExternalPermissionMayViewExternalProfile() {
        assertThat(ProfileVisibilityEvaluator.isProfileVisible(
                internal(Permission.VIEW_EXTERNAL_USER), profile(UserType.EXTERNAL, FIRM_ID))).isTrue();
    }

    @Test
    void internalActorWithoutViewExternalPermissionMayNotViewExternalProfile() {
        assertThat(ProfileVisibilityEvaluator.isProfileVisible(
                internal(), profile(UserType.EXTERNAL, FIRM_ID))).isFalse();
    }

    @Test
    void externalActorMayAlwaysViewTheirOwnActiveProfile() {
        AuthenticatedEntraUser actor = external(FIRM_ID);
        ProfileVisibilityContext ownProfile =
                new ProfileVisibilityContext(true, actor.activeProfile().id(), UserType.EXTERNAL, FIRM_ID);

        assertThat(ProfileVisibilityEvaluator.isProfileVisible(actor, ownProfile)).isTrue();
    }

    @Test
    void externalActorWithViewExternalPermissionMayViewColleagueInTheirOwnFirm() {
        assertThat(ProfileVisibilityEvaluator.isProfileVisible(
                external(FIRM_ID, Permission.VIEW_EXTERNAL_USER), profile(UserType.EXTERNAL, FIRM_ID))).isTrue();
    }

    @Test
    void externalActorWithoutViewExternalPermissionMayNotViewColleagueInTheirOwnFirm() {
        assertThat(ProfileVisibilityEvaluator.isProfileVisible(
                external(FIRM_ID), profile(UserType.EXTERNAL, FIRM_ID))).isFalse();
    }

    @Test
    void externalActorWithViewExternalPermissionMayNotViewProfileInAnotherFirm() {
        assertThat(ProfileVisibilityEvaluator.isProfileVisible(
                external(FIRM_ID, Permission.VIEW_EXTERNAL_USER),
                profile(UserType.EXTERNAL, UUID.randomUUID()))).isFalse();
    }

    @Test
    void externalActorMayNotViewAnInternalProfileEvenWithViewExternalPermission() {
        assertThat(ProfileVisibilityEvaluator.isProfileVisible(
                external(FIRM_ID, Permission.VIEW_EXTERNAL_USER), profile(UserType.INTERNAL, null))).isFalse();
    }

    @Test
    void internalActorMayNotViewAnExternalProfileWithOnlyViewInternalPermission() {
        assertThat(ProfileVisibilityEvaluator.isProfileVisible(
                internal(Permission.VIEW_INTERNAL_USER), profile(UserType.EXTERNAL, FIRM_ID))).isFalse();
    }

    private static ProfileVisibilityContext profile(UserType type, UUID firmId) {
        return new ProfileVisibilityContext(true, UUID.randomUUID(), type, firmId);
    }

    private static AuthenticatedEntraUser internal(Permission... permissions) {
        Set<AuthenticatedAppRole> roles = permissions.length == 0 ? Set.of()
                : Set.of(new AuthenticatedAppRole(UUID.randomUUID(), "Role", 1, "ROLE", false, null, null, "Role",
                        true, null, Set.of(permissions)));
        return actor(AuthenticatedUserProfile.builder().id(UUID.randomUUID()).activeProfile(true)
                .userType(UserType.INTERNAL).appRoles(roles).build());
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
}

package uk.gov.justice.laa.datauserapi.security.rbac;

import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedAppRole;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.model.Permission;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ActorPermissionsTest {

    @Test
    void returnsUnionOfActiveProfileRolePermissions() {
        AuthenticatedEntraUser actor = RbacTestActors.actorWith(Permission.DELETE_EXTERNAL_USER, Permission.VIEW_EXTERNAL_USER);

        assertThat(ActorPermissions.of(actor))
                .containsExactlyInAnyOrder(Permission.DELETE_EXTERNAL_USER, Permission.VIEW_EXTERNAL_USER);
        assertThat(ActorPermissions.has(actor, Permission.DELETE_EXTERNAL_USER)).isTrue();
        assertThat(ActorPermissions.has(actor, Permission.EDIT_EXTERNAL_USER)).isFalse();
    }

    @Test
    void ignoresInactiveProfiles() {
        AuthenticatedAppRole role = new AuthenticatedAppRole(UUID.randomUUID(), "r", 0, null, false,
                null, null, "d", true, null, Set.of(Permission.DELETE_EXTERNAL_USER));
        AuthenticatedUserProfile inactive = AuthenticatedUserProfile.builder()
                .activeProfile(false).userType(UserType.EXTERNAL).appRoles(Set.of(role)).build();
        AuthenticatedEntraUser actor = AuthenticatedEntraUser.builder()
                .userProfiles(Set.of(inactive)).activeProfile(null).build();

        assertThat(ActorPermissions.of(actor)).isEmpty();
    }

    @Test
    void handlesNullsSafely() {
        assertThat(ActorPermissions.of(null)).isEmpty();
        AuthenticatedUserProfile noRoles = AuthenticatedUserProfile.builder().activeProfile(true).build();
        assertThat(ActorPermissions.of(AuthenticatedEntraUser.builder().activeProfile(noRoles).build())).isEmpty();
    }
}

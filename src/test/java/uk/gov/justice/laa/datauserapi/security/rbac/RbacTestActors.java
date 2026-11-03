package uk.gov.justice.laa.datauserapi.security.rbac;

import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedAppRole;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.model.Permission;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

final class RbacTestActors {

    private RbacTestActors() {
    }

    static AuthenticatedEntraUser actorWith(Permission... permissions) {
        return actor(UserType.INTERNAL, null, permissions);
    }

    static AuthenticatedEntraUser externalActorWith(UUID firmId, Permission... permissions) {
        return actor(UserType.EXTERNAL, firmId, permissions);
    }

    static AuthenticatedEntraUser actorWithRole(String roleName, Permission... permissions) {
        return actor(UserType.INTERNAL, null, roleName, permissions);
    }

    static AuthenticatedEntraUser externalActorWithRole(UUID firmId, String roleName, Permission... permissions) {
        return actor(UserType.EXTERNAL, firmId, roleName, permissions);
    }

    private static AuthenticatedEntraUser actor(UserType userType, UUID firmId, Permission... permissions) {
        return actor(userType, firmId, "role", permissions);
    }

    private static AuthenticatedEntraUser actor(UserType userType, UUID firmId, String roleName, Permission... permissions) {
        AuthenticatedAppRole role = new AuthenticatedAppRole(UUID.randomUUID(), roleName, 0, null, false,
                null, null, "desc", true, null, Set.copyOf(Arrays.asList(permissions)));
        AuthenticatedUserProfile profile = AuthenticatedUserProfile.builder()
                .id(UUID.randomUUID())
                .activeProfile(true)
                .userType(userType)
                .firmId(firmId)
                .appRoles(Set.of(role))
                .build();
        return AuthenticatedEntraUser.builder()
                .id(UUID.randomUUID())
                .entraOid(UUID.randomUUID().toString())
                .userProfiles(Set.of(profile))
                .activeProfile(profile)
                .build();
    }
}

package uk.gov.justice.laa.datauserapi.security.rbac;

import uk.gov.justice.laa.datauserapi.dto.AuthenticatedAppRole;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.model.Permission;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves the effective permissions of an actor: the union of permissions granted by the
 * app roles assigned to the actor's <em>active</em> profile.
 */
public final class ActorPermissions {

    private ActorPermissions() {
    }

    public static Set<Permission> of(AuthenticatedEntraUser actor) {
        Set<Permission> permissions = EnumSet.noneOf(Permission.class);
        Optional.ofNullable(actor)
                .map(AuthenticatedEntraUser::activeProfile)
                .map(AuthenticatedUserProfile::appRoles)
                .stream()
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(AuthenticatedAppRole::permissions)
                .filter(Objects::nonNull)
                .forEach(permissions::addAll);
        return permissions;
    }

    public static boolean has(AuthenticatedEntraUser actor, Permission permission) {
        return of(actor).contains(permission);
    }
}

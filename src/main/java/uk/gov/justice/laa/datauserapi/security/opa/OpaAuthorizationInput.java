package uk.gov.justice.laa.datauserapi.security.opa;

import java.util.Set;

public record OpaAuthorizationInput(String action, Actor actor, Target target) {

    public record Actor(
            String entraOid,
            String userType,
            String firmId,
            Set<String> silasRoleIdentifiers,
            Set<String> permissions
    ) {
    }

    public record Target(
            String entraOid,
            String userType,
            String firmId,
            boolean multiFirm,
            boolean active,
            String deactivationType
    ) {
    }
}

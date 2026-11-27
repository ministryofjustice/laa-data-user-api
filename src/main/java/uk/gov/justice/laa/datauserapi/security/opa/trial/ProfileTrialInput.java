package uk.gov.justice.laa.datauserapi.security.opa.trial;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import uk.gov.justice.laa.datauserapi.contracts.domain.FirmType;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.model.Permission;

/** Fully materialised trusted facts; no JPA objects or lazy collections cross this boundary. */
public record ProfileTrialInput(ProfileTrialOperation operation, Actor actor, Target target,
                                Map<UUID, Role> roles, Set<UUID> additions, Set<UUID> removals,
                                boolean validRequest) {
    public ProfileTrialInput {
        roles = Map.copyOf(roles);
        additions = Set.copyOf(additions);
        removals = Set.copyOf(removals);
    }

    public record Actor(UUID id, UUID profileId, String entraOid, UserType userType, UUID firmId,
                        Set<String> roles, Set<UUID> roleIds, Set<Permission> permissions, boolean active) {
        public Actor {
            roles = Set.copyOf(roles);
            roleIds = Set.copyOf(roleIds);
            permissions = Set.copyOf(permissions);
        }
    }

    public record Target(UUID id, UserType userType, UUID firmId, FirmType firmType,
                         Set<UUID> roleIds) {
        public Target {
            roleIds = Set.copyOf(roleIds);
        }
    }

    public record Role(UUID id, UUID appId, String appName, String appEntraClientId, String appEntraOid,
                       String name, String identifier, String description, boolean enabled, boolean authzRole,
                       boolean legacySync, int ordinal, Set<UserType> userTypes, Set<FirmType> firmTypes,
                       Set<UUID> assigners) {
        public Role {
            userTypes = Set.copyOf(userTypes);
            firmTypes = Set.copyOf(firmTypes);
            assigners = Set.copyOf(assigners);
        }
    }
}

package uk.gov.justice.laa.datauserapi.security.opa.trial;

import java.util.Set;
import java.util.UUID;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;

public record ProfileTrialDecision(boolean allow, Reason reason, Filters queryFilters) {
    public enum Reason {
        ALLOWED, ACTOR_INACTIVE, TARGET_NOT_FOUND, INVALID_REQUEST, FIRM_SCOPE,
        MISSING_PERMISSION, INCOMPATIBLE_ROLE, ASSIGNMENT_RESTRICTED
    }

    public record Filters(UUID firmId, Set<UserType> userTypes, Set<UUID> roleIds) {
        public Filters {
            userTypes = Set.copyOf(userTypes);
            roleIds = Set.copyOf(roleIds);
        }
    }

    public static ProfileTrialDecision deny(Reason reason) {
        return new ProfileTrialDecision(false, reason, new Filters(null, Set.of(), Set.of()));
    }
}

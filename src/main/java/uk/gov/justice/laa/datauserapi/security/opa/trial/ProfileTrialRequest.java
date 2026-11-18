package uk.gov.justice.laa.datauserapi.security.opa.trial;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;

public record ProfileTrialRequest(ProfileTrialOperation operation, UUID profileId, Set<UUID> roleIds,
                                  Map<UUID, Set<UUID>> appsAndRoles, AuthenticatedEntraUser actor) {
    public ProfileTrialRequest {
        roleIds = Set.copyOf(roleIds);
        appsAndRoles = appsAndRoles.entrySet().stream().collect(
                java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> Set.copyOf(entry.getValue())));
    }
}

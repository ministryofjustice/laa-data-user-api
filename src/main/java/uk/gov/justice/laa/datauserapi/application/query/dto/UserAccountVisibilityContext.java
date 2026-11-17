package uk.gov.justice.laa.datauserapi.application.query.dto;

import java.util.Set;
import java.util.UUID;

public record UserAccountVisibilityContext(
        boolean userExists,
        boolean hasInternalProfile,
        boolean hasExternalProfile,
        Set<UUID> externalFirmIds,
        String targetUserOid
) {

    public static UserAccountVisibilityContext missing() {
        return new UserAccountVisibilityContext(false, false, false, Set.of(), null);
    }
}

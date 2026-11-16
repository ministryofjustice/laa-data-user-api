package uk.gov.justice.laa.datauserapi.application.query.dto;

import java.util.UUID;

/** Trusted facts about a target user account used to decide whether an actor may view it. */
public record UserAccountVisibilityContext(
        boolean userExists,
        boolean hasInternalProfile,
        boolean hasExternalProfile,
        UUID externalFirmId
) {

    public static UserAccountVisibilityContext missing() {
        return new UserAccountVisibilityContext(false, false, false, null);
    }
}

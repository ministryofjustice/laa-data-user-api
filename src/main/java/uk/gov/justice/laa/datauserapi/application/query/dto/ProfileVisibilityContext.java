package uk.gov.justice.laa.datauserapi.application.query.dto;

import java.util.UUID;

import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;

/** Trusted facts about a target profile used to decide whether an actor may view it. */
public record ProfileVisibilityContext(boolean profileExists, UUID profileId, UserType userType, UUID firmId) {

    public static ProfileVisibilityContext missing() {
        return new ProfileVisibilityContext(false, null, null, null);
    }
}

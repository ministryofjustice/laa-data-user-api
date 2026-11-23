package uk.gov.justice.laa.datauserapi.application.command.dto;

import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;

import java.util.UUID;

public record SetActiveProfileContext(
        boolean profileExists,
        UUID profileId,
        boolean profileBelongsToRequestedUser,
        UserType profileUserType
) {

    public static SetActiveProfileContext missing() {
        return new SetActiveProfileContext(false, null, false, null);
    }
}

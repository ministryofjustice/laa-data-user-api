package uk.gov.justice.laa.datauserapi.command;

import java.util.UUID;

public record CreateUserAccountResult(
        boolean success,
        String message,
        String userEntraObjectId,
        UUID userProfileId
) {}

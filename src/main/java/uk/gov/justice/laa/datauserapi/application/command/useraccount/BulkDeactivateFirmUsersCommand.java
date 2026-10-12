package uk.gov.justice.laa.datauserapi.application.command.useraccount;

import jakarta.validation.constraints.NotNull;
import uk.gov.justice.laa.datauserapi.contracts.domain.DeactivateUserReason;

import java.util.UUID;

public record BulkDeactivateFirmUsersCommand(
        @NotNull UUID firmId,
        @NotNull DeactivateUserReason deactivateReason
) {}

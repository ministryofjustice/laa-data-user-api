package uk.gov.justice.laa.datauserapi.application.command.useraccount;

import jakarta.validation.constraints.NotNull;
import uk.gov.justice.laa.datauserapi.contracts.domain.DeactivateUserReason;

public record BulkDeactivateFirmUsersCommand(
        @NotNull String firmId,
        @NotNull DeactivateUserReason deactivateReason
) {}

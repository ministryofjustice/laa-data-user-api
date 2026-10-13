package uk.gov.justice.laa.datauserapi.contracts.request;

import jakarta.validation.constraints.NotNull;
import uk.gov.justice.laa.datauserapi.contracts.domain.DeactivateUserReason;

public record BulkDeactivateFirmUsersRequest(
        @NotNull String firmId,
        @NotNull DeactivateUserReason deactivateReason
) {}

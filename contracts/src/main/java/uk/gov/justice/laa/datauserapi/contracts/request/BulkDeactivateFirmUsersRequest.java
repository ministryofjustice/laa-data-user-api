package uk.gov.justice.laa.datauserapi.contracts.request;

import jakarta.validation.constraints.NotNull;
import uk.gov.justice.laa.datauserapi.contracts.domain.DeactivateUserReason;

import java.util.UUID;

public record BulkDeactivateFirmUsersRequest(
        @NotNull UUID firmId,
        @NotNull DeactivateUserReason deactivateReason
) {}

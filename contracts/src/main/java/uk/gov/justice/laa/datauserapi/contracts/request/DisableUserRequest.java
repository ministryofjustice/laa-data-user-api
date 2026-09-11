package uk.gov.justice.laa.datauserapi.contracts.request;

import jakarta.validation.constraints.NotNull;
import uk.gov.justice.laa.datauserapi.contracts.DisableUserReason;

/**
 * Request to disable a SiLAS user account.
 *
 * @param disableReasonId the reason for disabling the account (required)
 */
public record DisableUserRequest(
        @NotNull DisableUserReason disableReasonId
) {}

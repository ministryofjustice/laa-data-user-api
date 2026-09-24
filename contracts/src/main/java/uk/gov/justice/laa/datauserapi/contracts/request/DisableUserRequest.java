package uk.gov.justice.laa.datauserapi.contracts.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request to disable a SiLAS user account.
 *
 * @param disableReason the reason for disabling the account (required)
 */
public record DisableUserRequest(
        @NotNull UUID userEntraObjectId,
        @NotNull String disableReason
) {}

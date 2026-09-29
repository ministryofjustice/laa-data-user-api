package uk.gov.justice.laa.datauserapi.contracts.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request to deactivate a SiLAS user account.
 *
 * @param deactivateReason the reason for deactivating the account (required)
 */
public record DeactivateUserRequest(
        @NotNull UUID userEntraObjectId,
        @NotNull String deactivateReason
) {}

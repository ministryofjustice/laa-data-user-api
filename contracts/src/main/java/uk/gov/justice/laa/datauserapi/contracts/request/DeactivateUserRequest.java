package uk.gov.justice.laa.datauserapi.contracts.request;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.util.UUID;

/**
 * Request to deactivate a SiLAS user account.
 *
 * @param deactivateReason the reason for deactivating the account (required)
 */
@Builder
public record DeactivateUserRequest(
        @NotNull UUID userEntraObjectId,
        @NotNull String deactivateReason
) {}

package uk.gov.justice.laa.datauserapi.contracts.request;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import uk.gov.justice.laa.datauserapi.contracts.domain.DeleteUserReason;

import java.util.UUID;

/**
 * Request to delete a user account and all of its profiles.
 *
 * @param userEntraObjectId the Entra object ID of the user to delete (required)
 * @param deleteReason      the reason for deleting the account (required)
 */
@Builder
public record DeleteUserRequest(
        @NotNull UUID userEntraObjectId,
        @NotNull DeleteUserReason deleteReason
) {}

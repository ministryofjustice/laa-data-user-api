package uk.gov.justice.laa.datauserapi.application.command.useraccount;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import uk.gov.justice.laa.datauserapi.contracts.domain.DeleteUserReason;

import java.util.UUID;

@Builder
public record DeleteUserCommand(
        @NotNull UUID userEntraObjectId,
        @NotNull DeleteUserReason deleteReason
) {}

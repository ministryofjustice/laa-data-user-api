package uk.gov.justice.laa.datauserapi.application.command.useraccount;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import uk.gov.justice.laa.datauserapi.contracts.domain.DeactivateUserReason;

import java.util.UUID;

@Builder
public record DeactivateUserCommand(
        @NotNull UUID userEntraObjectId,
        @NotNull DeactivateUserReason deactivateReason
) {}

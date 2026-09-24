package uk.gov.justice.laa.datauserapi.application.command.useraccount;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import uk.gov.justice.laa.datauserapi.contracts.domain.DisableUserReason;

import java.util.UUID;

@Builder
public record DisableUserCommand(
        @NotNull UUID userEntraObjectId,
        @NotNull DisableUserReason disableReason
) {}

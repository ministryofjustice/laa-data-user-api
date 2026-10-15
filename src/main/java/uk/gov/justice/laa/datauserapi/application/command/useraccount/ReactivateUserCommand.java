package uk.gov.justice.laa.datauserapi.application.command.useraccount;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.util.UUID;

@Builder
public record ReactivateUserCommand(
        @NotNull UUID userEntraObjectId,
        String comments
) {}

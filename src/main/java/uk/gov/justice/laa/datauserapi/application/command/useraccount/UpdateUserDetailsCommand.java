package uk.gov.justice.laa.datauserapi.application.command.useraccount;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.util.UUID;

@Builder
public record UpdateUserDetailsCommand(
        @NotNull UUID userEntraObjectId,
        String firstName,
        String lastName,
        String email
) {}

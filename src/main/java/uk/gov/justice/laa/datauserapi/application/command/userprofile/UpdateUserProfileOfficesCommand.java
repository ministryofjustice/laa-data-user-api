package uk.gov.justice.laa.datauserapi.application.command.userprofile;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.util.Set;
import java.util.UUID;

@Builder
public record UpdateUserProfileOfficesCommand(
        @NotNull UUID userProfileId,
        @NotNull Set<UUID> officeIds
) {}

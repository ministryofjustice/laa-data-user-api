package uk.gov.justice.laa.datauserapi.application.command.userprofile;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.util.UUID;

@Builder
public record UpdateUserProfileUnrestrictedOfficesCommand(
        @NotNull UUID userProfileId,
        @NotNull Boolean unrestrictedOfficeAccess
) {}

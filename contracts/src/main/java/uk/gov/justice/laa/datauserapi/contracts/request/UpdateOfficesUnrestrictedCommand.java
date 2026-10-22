package uk.gov.justice.laa.datauserapi.contracts.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record UpdateOfficesUnrestrictedCommand(
        @NotNull UUID userProfileId,
        @NotNull Boolean unrestrictedOfficeAccess
) implements UpdateOfficesRequest {}

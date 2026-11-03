package uk.gov.justice.laa.datauserapi.contracts.request;

import jakarta.validation.constraints.NotNull;

import java.util.Set;
import java.util.UUID;

public record UpdateUserProfileOfficesRequest(
        @NotNull UUID userProfileId,
        @NotNull Set<UUID> officeIds
) implements UpdateOfficesRequest {}

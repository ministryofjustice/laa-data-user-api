package uk.gov.justice.laa.datauserapi.contracts.request;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateAppRolesRequest(@NotNull UUID userProfileId,
                                    @NotNull @Size(max = 100) List<@NotNull @Valid AppRoles> appsAndRoles) {
    public record AppRoles(@NotNull UUID appId, @NotNull @Size(max = 100) Set<@NotNull UUID> appRoleIds) {
    }
}

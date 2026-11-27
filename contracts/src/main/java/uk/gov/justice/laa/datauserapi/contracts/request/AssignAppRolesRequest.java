package uk.gov.justice.laa.datauserapi.contracts.request;

import java.util.Set;
import java.util.UUID;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AssignAppRolesRequest(@NotNull UUID userProfileId,
                                    @NotNull @Size(max = 500) Set<@NotNull UUID> appRoleIds) {
}

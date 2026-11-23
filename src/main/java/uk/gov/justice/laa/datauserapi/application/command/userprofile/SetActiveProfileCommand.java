package uk.gov.justice.laa.datauserapi.application.command.userprofile;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import uk.gov.justice.laa.datauserapi.application.command.policy.SetActiveProfilePolicy;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyGuarded;

import java.util.UUID;

@Builder
@PolicyGuarded(policies = SetActiveProfilePolicy.class)
public record SetActiveProfileCommand(
        @NotNull UUID userEntraObjectId,
        @NotNull UUID userProfileId
) {
}

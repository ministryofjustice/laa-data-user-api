package uk.gov.justice.laa.datauserapi.application.command.dto;

import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.model.DeactivationType;

import java.util.Optional;
import java.util.Objects;
import java.util.UUID;

public record UserLifecycleAuthorizationContext(
        UserType userType,
        UUID firmId,
        boolean multiFirm,
        boolean active,
        Optional<DeactivationType> deactivationType
) {
    public UserLifecycleAuthorizationContext {
        Objects.requireNonNull(deactivationType, "deactivationType");
    }
}

package uk.gov.justice.laa.datauserapi.application.command.dto;

import java.util.Objects;
import java.util.Optional;
import uk.gov.justice.laa.datauserapi.model.DeactivationType;

/**
 * A snapshot of account deactivation facts. An empty type explicitly means legacy, unattributed
 * deactivation; it is not equivalent to the recorded NONE type.
 */
public record UserDeactivationContext(boolean deactivated, Optional<DeactivationType> deactivationType) {

    public UserDeactivationContext {
        Objects.requireNonNull(deactivationType, "deactivationType");
    }
}

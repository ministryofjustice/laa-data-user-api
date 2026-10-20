package uk.gov.justice.laa.datauserapi.application.command.useraccount;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyGuarded;
import uk.gov.justice.laa.datauserapi.security.rbac.TargetsUser;

import java.util.UUID;

@Builder
@PolicyGuarded
public record ReactivateUserCommand(
        @NotNull UUID userEntraObjectId,
        String comments
) implements TargetsUser {

    @Override
    public UUID targetUserEntraObjectId() {
        return userEntraObjectId;
    }
}

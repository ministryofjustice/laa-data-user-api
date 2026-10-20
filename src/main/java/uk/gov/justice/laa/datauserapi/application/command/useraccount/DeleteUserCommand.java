package uk.gov.justice.laa.datauserapi.application.command.useraccount;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import uk.gov.justice.laa.datauserapi.contracts.domain.DeleteUserReason;
import uk.gov.justice.laa.datauserapi.model.Permission;
import uk.gov.justice.laa.datauserapi.security.rbac.RequiresPermission;
import uk.gov.justice.laa.datauserapi.security.rbac.TargetsUser;

import java.util.UUID;

@Builder
@RequiresPermission(Permission.DELETE_EXTERNAL_USER)
public record DeleteUserCommand(
        @NotNull UUID userEntraObjectId,
        @NotNull DeleteUserReason deleteReason
) implements TargetsUser {

    @Override
    public UUID targetUserEntraObjectId() {
        return userEntraObjectId;
    }
}

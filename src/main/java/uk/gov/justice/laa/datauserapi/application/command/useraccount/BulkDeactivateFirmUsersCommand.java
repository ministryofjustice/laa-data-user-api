package uk.gov.justice.laa.datauserapi.application.command.useraccount;

import jakarta.validation.constraints.NotNull;
import uk.gov.justice.laa.datauserapi.contracts.domain.DeactivateUserReason;
import uk.gov.justice.laa.datauserapi.model.Permission;
import uk.gov.justice.laa.datauserapi.security.rbac.RequiresPermission;
import uk.gov.justice.laa.datauserapi.security.rbac.TargetsFirm;

@RequiresPermission(Permission.BULK_DISABLE_FIRM_USERS)
public record BulkDeactivateFirmUsersCommand(
        @NotNull String firmId,
        @NotNull DeactivateUserReason deactivateReason
) implements TargetsFirm {

    @Override
    public String targetFirmCode() {
        return firmId;
    }
}

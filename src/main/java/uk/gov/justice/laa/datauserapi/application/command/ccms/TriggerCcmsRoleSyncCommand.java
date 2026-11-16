package uk.gov.justice.laa.datauserapi.application.command.ccms;

import uk.gov.justice.laa.datauserapi.model.Permission;
import uk.gov.justice.laa.datauserapi.security.rbac.RequiresPermission;

@RequiresPermission(Permission.TRIGGER_CCMS_ROLE_SYNC)
public record TriggerCcmsRoleSyncCommand() {
}

package uk.gov.justice.laa.datauserapi.application.command.userprofile;

import uk.gov.justice.laa.datauserapi.model.Permission;
import uk.gov.justice.laa.datauserapi.security.rbac.RequiresPermission;

@RequiresPermission(Permission.UPDATE_USER_PROFILE_SILAS_STATUS)
public record RefreshSilasStatusCommand() {
}

package uk.gov.justice.laa.datauserapi.application.command.userprofile;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import uk.gov.justice.laa.datauserapi.application.command.policy.ExternalProfileTargetOnlyPolicy;
import uk.gov.justice.laa.datauserapi.application.command.policy.NotSelfProfileTargetPolicy;
import uk.gov.justice.laa.datauserapi.model.Permission;
import uk.gov.justice.laa.datauserapi.security.rbac.RequiresPermission;
import uk.gov.justice.laa.datauserapi.security.rbac.TargetsUserProfile;

import java.util.UUID;

@Builder
@RequiresPermission(value = Permission.EDIT_USER_OFFICE,
        policies = {NotSelfProfileTargetPolicy.class,
                    ExternalProfileTargetOnlyPolicy.class})
public record UpdateUserProfileUnrestrictedOfficesCommand(
        @NotNull UUID userProfileId,
        @NotNull Boolean unrestrictedOfficeAccess
) implements TargetsUserProfile {

    @Override
    public UUID targetUserProfileId() {
        return userProfileId;
    }
}

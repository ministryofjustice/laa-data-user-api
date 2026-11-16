package uk.gov.justice.laa.datauserapi.application.command.useraccount;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import uk.gov.justice.laa.datauserapi.application.command.policy.ExternalTargetOnlyPolicy;
import uk.gov.justice.laa.datauserapi.application.command.policy.NotSelfTargetPolicy;
import uk.gov.justice.laa.datauserapi.application.command.policy.ReactivationHierarchyPolicy;
import uk.gov.justice.laa.datauserapi.model.Permission;
import uk.gov.justice.laa.datauserapi.security.rbac.RequiresPermission;
import uk.gov.justice.laa.datauserapi.security.rbac.TargetsUser;

import java.util.UUID;

@Builder
@RequiresPermission(value = Permission.ENABLE_EXTERNAL_USER,
        policies = {NotSelfTargetPolicy.class,
                    ExternalTargetOnlyPolicy.class,
                    ReactivationHierarchyPolicy.class})
public record ReactivateUserCommand(
        @NotNull UUID userEntraObjectId,
        String comments
) implements TargetsUser {

    @Override
    public UUID targetUserEntraObjectId() {
        return userEntraObjectId;
    }
}

package uk.gov.justice.laa.datauserapi.application.command.useraccount;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import uk.gov.justice.laa.datauserapi.application.command.policy.ExternalTargetOnlyPolicy;
import uk.gov.justice.laa.datauserapi.application.command.policy.NotSelfTargetPolicy;
import uk.gov.justice.laa.datauserapi.application.command.policy.TargetNotDeactivatedPolicy;
import uk.gov.justice.laa.datauserapi.contracts.domain.DeactivateUserReason;
import uk.gov.justice.laa.datauserapi.model.Permission;
import uk.gov.justice.laa.datauserapi.security.rbac.RequiresPermission;
import uk.gov.justice.laa.datauserapi.security.rbac.TargetsUser;

import java.util.UUID;

@Builder
@RequiresPermission(value = Permission.DISABLE_EXTERNAL_USER,
        policies = {NotSelfTargetPolicy.class,
                    ExternalTargetOnlyPolicy.class,
                    TargetNotDeactivatedPolicy.class})
public record DeactivateUserCommand(
        @NotNull UUID userEntraObjectId,
        @NotNull DeactivateUserReason deactivateReason
) implements TargetsUser {

    @Override
    public UUID targetUserEntraObjectId() {
        return userEntraObjectId;
    }
}

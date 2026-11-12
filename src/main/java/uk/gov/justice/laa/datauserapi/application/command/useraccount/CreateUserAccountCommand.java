package uk.gov.justice.laa.datauserapi.application.command.useraccount;

import uk.gov.justice.laa.datauserapi.application.command.policy.InternalActorOnlyPolicy;
import uk.gov.justice.laa.datauserapi.model.Permission;
import uk.gov.justice.laa.datauserapi.security.rbac.RequiresPermission;

@RequiresPermission(value = Permission.CREATE_EXTERNAL_USER, policies = InternalActorOnlyPolicy.class)
public record CreateUserAccountCommand(
        String email,
        String firstName,
        String lastName,
        String userType,
        Boolean multiFirmUser,
        String firmId
) {}

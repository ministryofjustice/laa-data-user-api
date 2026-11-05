package uk.gov.justice.laa.datauserapi.application.query.queryuserbyid;

import uk.gov.justice.laa.datauserapi.application.query.policy.GetUserAccountAuthorizationPolicy;
import uk.gov.justice.laa.datauserapi.model.Permission;
import uk.gov.justice.laa.datauserapi.security.rbac.RequiresPermission;
import uk.gov.justice.laa.datauserapi.security.rbac.TargetsUser;

import java.util.UUID;

@RequiresPermission(
        value = {Permission.VIEW_INTERNAL_USER, Permission.VIEW_EXTERNAL_USER},
        match = RequiresPermission.Match.ANY,
        policies = {GetUserAccountAuthorizationPolicy.class}
)
public record GetUserAccountQuery(
        String userEntraObjectId,
        String actorOid
) implements TargetsUser {

    @Override
    public UUID targetUserEntraObjectId() {
        return UUID.fromString(userEntraObjectId);
    }
}

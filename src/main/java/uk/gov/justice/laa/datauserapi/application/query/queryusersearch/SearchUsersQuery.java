package uk.gov.justice.laa.datauserapi.application.query.queryusersearch;

import uk.gov.justice.laa.datauserapi.application.query.policy.SearchUsersAuthorizationPolicy;
import uk.gov.justice.laa.datauserapi.contracts.dto.UserSearchCriteria;
import uk.gov.justice.laa.datauserapi.model.Permission;
import uk.gov.justice.laa.datauserapi.security.rbac.RequiresPermission;

@RequiresPermission(
        value = {Permission.VIEW_INTERNAL_USER, Permission.VIEW_EXTERNAL_USER},
        match = RequiresPermission.Match.ANY,
        policies = {SearchUsersAuthorizationPolicy.class}
)
public record SearchUsersQuery(
        UserSearchCriteria criteria,
        String actorOid
) {}

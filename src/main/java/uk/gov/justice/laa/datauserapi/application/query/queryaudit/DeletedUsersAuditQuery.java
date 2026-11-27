package uk.gov.justice.laa.datauserapi.application.query.queryaudit;

import uk.gov.justice.laa.datauserapi.application.query.policy.CanViewDeletedUsersAuditPolicy;
import uk.gov.justice.laa.datauserapi.contracts.dto.AuditDeletedUsersCriteria;
import uk.gov.justice.laa.datauserapi.model.Permission;
import uk.gov.justice.laa.datauserapi.security.rbac.RequiresPermission;

@RequiresPermission(value = Permission.VIEW_AUDIT_TABLE, policies = CanViewDeletedUsersAuditPolicy.class)
public record DeletedUsersAuditQuery(AuditDeletedUsersCriteria criteria) {
}

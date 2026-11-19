package uk.gov.justice.laa.datauserapi.application.query.queryaudit;

import uk.gov.justice.laa.datauserapi.application.query.policy.CanViewAuditUserPolicy;
import uk.gov.justice.laa.datauserapi.exception.InvalidUuidFormatException;
import uk.gov.justice.laa.datauserapi.model.Permission;
import uk.gov.justice.laa.datauserapi.security.rbac.RequiresPermission;

import java.util.UUID;

@RequiresPermission(value = Permission.VIEW_AUDIT_TABLE, policies = CanViewAuditUserPolicy.class)
public record AuditUserDetailQuery(String userEntraObjectId) {

    public AuditUserDetailQuery {
        validateUuidFormat(userEntraObjectId);
    }

    private static void validateUuidFormat(String value) {
        try {
            UUID.fromString(value);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new InvalidUuidFormatException("userEntraObjectId must match UUID format");
        }
    }
}

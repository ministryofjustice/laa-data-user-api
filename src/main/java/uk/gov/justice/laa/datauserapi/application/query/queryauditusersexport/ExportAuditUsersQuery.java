package uk.gov.justice.laa.datauserapi.application.query.queryauditusersexport;

import uk.gov.justice.laa.datauserapi.application.query.policy.CanExportAuditUsersPolicy;
import uk.gov.justice.laa.datauserapi.contracts.dto.AuditUserExportCriteria;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyGuarded;

@PolicyGuarded(policies = CanExportAuditUsersPolicy.class)
public record ExportAuditUsersQuery(
        AuditUserExportCriteria criteria
) {}

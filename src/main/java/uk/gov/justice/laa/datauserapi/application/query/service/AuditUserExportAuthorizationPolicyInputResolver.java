package uk.gov.justice.laa.datauserapi.application.query.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.query.dto.AuditUserExportAuthorizationContext;
import uk.gov.justice.laa.datauserapi.application.query.policy.CanExportAuditUsersPolicy;
import uk.gov.justice.laa.datauserapi.application.query.queryauditusersexport.ExportAuditUsersQuery;
import uk.gov.justice.laa.datauserapi.security.rbac.AuthorizationPolicy;
import uk.gov.justice.laa.datauserapi.security.rbac.AuthorizationPolicyInputResolver;

/**
 * Resolves authorization facts for {@link CanExportAuditUsersPolicy} directly from the query's
 * criteria; no persistence lookups are required.
 */
@Service
@Transactional(readOnly = true)
public class AuditUserExportAuthorizationPolicyInputResolver
        implements AuthorizationPolicyInputResolver<ExportAuditUsersQuery, AuditUserExportAuthorizationContext> {

    @Override
    public Class<? extends AuthorizationPolicy<AuditUserExportAuthorizationContext>> policyType() {
        return CanExportAuditUsersPolicy.class;
    }

    @Override
    public Class<ExportAuditUsersQuery> messageType() {
        return ExportAuditUsersQuery.class;
    }

    @Override
    public AuditUserExportAuthorizationContext resolve(ExportAuditUsersQuery message) {
        return new AuditUserExportAuthorizationContext(
                message.criteria().userType(),
                message.criteria().firmId(),
                message.criteria().isScopedToOneFirmOrInternalOnly());
    }
}

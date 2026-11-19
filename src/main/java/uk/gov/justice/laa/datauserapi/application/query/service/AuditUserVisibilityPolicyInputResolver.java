package uk.gov.justice.laa.datauserapi.application.query.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import uk.gov.justice.laa.datauserapi.application.query.dto.UserAccountVisibilityContext;
import uk.gov.justice.laa.datauserapi.application.query.mapper.UserAccountVisibilityContextFactory;
import uk.gov.justice.laa.datauserapi.application.query.policy.CanViewAuditUserPolicy;
import uk.gov.justice.laa.datauserapi.application.query.queryaudit.AuditUserDetailQuery;
import uk.gov.justice.laa.datauserapi.security.rbac.AuthorizationPolicy;
import uk.gov.justice.laa.datauserapi.security.rbac.AuthorizationPolicyInputResolver;

/**
 * Loads, with a single read, the trusted target-user facts that {@link CanViewAuditUserPolicy} evaluates.
 * An unknown target resolves to a missing context, which the policy denies, so unknown and inaccessible targets
 * are indistinguishable to the caller (403).
 */
@Component
@Transactional(readOnly = true)
public class AuditUserVisibilityPolicyInputResolver
        implements AuthorizationPolicyInputResolver<AuditUserDetailQuery, UserAccountVisibilityContext> {

    private final UserAccountQueryService queryService;
    private final UserAccountVisibilityContextFactory contextFactory;

    public AuditUserVisibilityPolicyInputResolver(UserAccountQueryService queryService,
                                                  UserAccountVisibilityContextFactory contextFactory) {
        this.queryService = queryService;
        this.contextFactory = contextFactory;
    }

    @Override
    public Class<? extends AuthorizationPolicy<UserAccountVisibilityContext>> policyType() {
        return CanViewAuditUserPolicy.class;
    }

    @Override
    public Class<AuditUserDetailQuery> messageType() {
        return AuditUserDetailQuery.class;
    }

    @Override
    public UserAccountVisibilityContext resolve(AuditUserDetailQuery message) {
        // Avoids exception-driven flow: a thrown lookup failure would mark the surrounding read-only transaction
        // rollback-only even though the miss is handled here.
        return queryService.findUserAccountSummaryIfPresent(message.userEntraObjectId())
                .map(contextFactory::fromUser)
                .orElseGet(UserAccountVisibilityContext::missing);
    }
}

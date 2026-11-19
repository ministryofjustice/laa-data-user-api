package uk.gov.justice.laa.datauserapi.application.query.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import uk.gov.justice.laa.datauserapi.application.query.dto.UserAccountVisibilityContext;
import uk.gov.justice.laa.datauserapi.application.query.mapper.UserAccountVisibilityContextFactory;
import uk.gov.justice.laa.datauserapi.application.query.policy.CanViewAuditUserPolicy;
import uk.gov.justice.laa.datauserapi.application.query.queryaudit.AuditUserDetailQuery;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.AuditUserAccessRepository;
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

    private final AuditUserAccessRepository accessRepository;
    private final UserAccountVisibilityContextFactory contextFactory;

    public AuditUserVisibilityPolicyInputResolver(AuditUserAccessRepository accessRepository,
                                                  UserAccountVisibilityContextFactory contextFactory) {
        this.accessRepository = accessRepository;
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
        String oid = message.userEntraObjectId();
        // Absent accounts are an ordinary outcome here, so no exception is thrown from inside the transaction.
        return accessRepository.findAccount(oid)
                .map(account -> contextFactory.fromFacts(
                        account.entraOid(), account.multiFirmUser(), accessRepository.findProfileRows(oid)))
                .orElseGet(UserAccountVisibilityContext::missing);
    }
}

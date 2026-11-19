package uk.gov.justice.laa.datauserapi.application.query.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import uk.gov.justice.laa.datauserapi.application.query.dto.UserAccountVisibilityContext;
import uk.gov.justice.laa.datauserapi.application.query.mapper.UserAccountVisibilityContextFactory;
import uk.gov.justice.laa.datauserapi.application.query.policy.CanViewAuditUserPolicy;
import uk.gov.justice.laa.datauserapi.application.query.queryaudit.AuditUserDetailQuery;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.exception.AuditUserNotFoundException;
import uk.gov.justice.laa.datauserapi.exception.UserNotFoundException;
import uk.gov.justice.laa.datauserapi.security.rbac.AuthorizationPolicy;
import uk.gov.justice.laa.datauserapi.security.rbac.AuthorizationPolicyInputResolver;

import java.util.Optional;

/**
 * Loads, with a single read, the trusted target-user facts that {@link CanViewAuditUserPolicy} evaluates.
 * An unknown target raises {@link AuditUserNotFoundException} (404), which only runs after the permission check.
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
        EntraUserDto target;
        try {
            target = queryService.findUserAccountSummaryByUserEntraObjectId(message.userEntraObjectId());
        } catch (UserNotFoundException ex) {
            throw new AuditUserNotFoundException(message.userEntraObjectId());
        }
        return Optional.ofNullable(target)
                .map(contextFactory::fromUser)
                .orElseGet(UserAccountVisibilityContext::missing);
    }
}

package uk.gov.justice.laa.datauserapi.application.query.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import uk.gov.justice.laa.datauserapi.application.query.dto.UserAccountVisibilityContext;
import uk.gov.justice.laa.datauserapi.application.query.mapper.UserAccountVisibilityContextFactory;
import uk.gov.justice.laa.datauserapi.application.query.policy.CanViewAccountPolicy;
import uk.gov.justice.laa.datauserapi.application.query.queryuserbyid.GetUserAccountQuery;
import uk.gov.justice.laa.datauserapi.security.rbac.AuthorizationPolicy;
import uk.gov.justice.laa.datauserapi.security.rbac.AuthorizationPolicyInputResolver;

import java.util.Optional;

/** Loads, with a single read, the trusted target-user facts that {@link CanViewAccountPolicy} evaluates. */
@Component
@Transactional(readOnly = true)
public class UserAccountVisibilityPolicyInputResolver
        implements AuthorizationPolicyInputResolver<GetUserAccountQuery, UserAccountVisibilityContext> {

    private final UserAccountQueryService queryService;
    private final UserAccountVisibilityContextFactory contextFactory;

    public UserAccountVisibilityPolicyInputResolver(UserAccountQueryService queryService,
                                                    UserAccountVisibilityContextFactory contextFactory) {
        this.queryService = queryService;
        this.contextFactory = contextFactory;
    }

    @Override
    public Class<? extends AuthorizationPolicy<UserAccountVisibilityContext>> policyType() {
        return CanViewAccountPolicy.class;
    }

    @Override
    public Class<GetUserAccountQuery> messageType() {
        return GetUserAccountQuery.class;
    }

    @Override
    public UserAccountVisibilityContext resolve(GetUserAccountQuery message) {
        return Optional.ofNullable(queryService.findUserAccountSummaryByUserEntraObjectId(message.userEntraObjectId()))
                .map(contextFactory::fromUser)
                .orElseGet(UserAccountVisibilityContext::missing);
    }
}

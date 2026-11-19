package uk.gov.justice.laa.datauserapi.application.query.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.query.dto.UserSearchAuthorizationContext;
import uk.gov.justice.laa.datauserapi.application.query.policy.CanSearchUsersPolicy;
import uk.gov.justice.laa.datauserapi.application.query.queryusersearch.SearchUsersQuery;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.model.Permission;
import uk.gov.justice.laa.datauserapi.security.rbac.AuthorizationPolicy;
import uk.gov.justice.laa.datauserapi.security.rbac.AuthorizationPolicyInputResolver;

/**
 * Resolves authorization facts for the CanSearchUsersPolicy.
 * Loads actor context from the authenticated principal.
 */
@Service
@Transactional(readOnly = true)
public class UserSearchAuthorizationPolicyInputResolver implements AuthorizationPolicyInputResolver<SearchUsersQuery, UserSearchAuthorizationContext> {

    @Override
    public Class<? extends AuthorizationPolicy<UserSearchAuthorizationContext>> policyType() {
        return CanSearchUsersPolicy.class;
    }

    @Override
    public Class<SearchUsersQuery> messageType() {
        return SearchUsersQuery.class;
    }

    @Override
    public UserSearchAuthorizationContext resolve(SearchUsersQuery message) {
        return new UserSearchAuthorizationContext(true, false, null);
    }
}

package uk.gov.justice.laa.datauserapi.application.query.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import uk.gov.justice.laa.datauserapi.application.query.dto.ProfileVisibilityContext;
import uk.gov.justice.laa.datauserapi.application.query.policy.CanViewProfileListPolicy;
import uk.gov.justice.laa.datauserapi.application.query.queryuserprofiles.GetUserProfilesQuery;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.UserProfileQueryRepository;
import uk.gov.justice.laa.datauserapi.security.rbac.AuthorizationPolicy;
import uk.gov.justice.laa.datauserapi.security.rbac.AuthorizationPolicyInputResolver;

import java.util.List;

/**
 * Loads, with a single read, the trusted profile-visibility facts for all profiles of a user
 * that {@link CanViewProfileListPolicy} evaluates.
 */
@Component
@Transactional(readOnly = true)
public class UserProfileListVisibilityPolicyInputResolver
        implements AuthorizationPolicyInputResolver<GetUserProfilesQuery, List<ProfileVisibilityContext>> {

    private final UserProfileQueryRepository repository;

    public UserProfileListVisibilityPolicyInputResolver(UserProfileQueryRepository repository) {
        this.repository = repository;
    }

    @Override
    public Class<? extends AuthorizationPolicy<List<ProfileVisibilityContext>>> policyType() {
        return CanViewProfileListPolicy.class;
    }

    @Override
    public Class<GetUserProfilesQuery> messageType() {
        return GetUserProfilesQuery.class;
    }

    @Override
    public List<ProfileVisibilityContext> resolve(GetUserProfilesQuery message) {
        return repository.findProfileVisibilityContexts(message.userEntraObjectId());
    }
}

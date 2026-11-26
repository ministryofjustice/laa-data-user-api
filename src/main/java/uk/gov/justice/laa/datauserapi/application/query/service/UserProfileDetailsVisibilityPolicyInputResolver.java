package uk.gov.justice.laa.datauserapi.application.query.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import uk.gov.justice.laa.datauserapi.application.query.dto.ProfileVisibilityContext;
import uk.gov.justice.laa.datauserapi.application.query.policy.CanViewProfileDetailsPolicy;
import uk.gov.justice.laa.datauserapi.application.query.queryuserprofiles.UserProfileQuery;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.UserProfileQueryRepository;
import uk.gov.justice.laa.datauserapi.security.rbac.AuthorizationPolicy;
import uk.gov.justice.laa.datauserapi.security.rbac.AuthorizationPolicyInputResolver;

/** Loads, with a single read, the trusted target-profile facts that {@link CanViewProfileDetailsPolicy} evaluates. */
@Component
@Transactional(readOnly = true)
public class UserProfileDetailsVisibilityPolicyInputResolver
        implements AuthorizationPolicyInputResolver<UserProfileQuery, ProfileVisibilityContext> {

    private final UserProfileQueryRepository repository;

    public UserProfileDetailsVisibilityPolicyInputResolver(UserProfileQueryRepository repository) {
        this.repository = repository;
    }

    @Override
    public Class<? extends AuthorizationPolicy<ProfileVisibilityContext>> policyType() {
        return CanViewProfileDetailsPolicy.class;
    }

    @Override
    public Class<UserProfileQuery> messageType() {
        return UserProfileQuery.class;
    }

    @Override
    public ProfileVisibilityContext resolve(UserProfileQuery message) {
        return repository.findActiveProfileVisibilityContext(message.userEntraObjectId())
                .orElseGet(ProfileVisibilityContext::missing);
    }
}

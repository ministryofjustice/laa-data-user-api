package uk.gov.justice.laa.datauserapi.application.query.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import uk.gov.justice.laa.datauserapi.application.query.dto.ProfileVisibilityContext;
import uk.gov.justice.laa.datauserapi.application.query.policy.CanViewProfilePolicy;
import uk.gov.justice.laa.datauserapi.application.query.queryuserprofileaccess.UserProfileAccessQuery;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.UserProfileAccessQueryRepository;
import uk.gov.justice.laa.datauserapi.security.rbac.AuthorizationPolicy;
import uk.gov.justice.laa.datauserapi.security.rbac.AuthorizationPolicyInputResolver;

/** Loads, with a single read, the trusted target-profile facts that {@link CanViewProfilePolicy} evaluates. */
@Component
@Transactional(readOnly = true)
public class UserProfileVisibilityPolicyInputResolver
        implements AuthorizationPolicyInputResolver<UserProfileAccessQuery, ProfileVisibilityContext> {

    private final UserProfileAccessQueryRepository repository;

    public UserProfileVisibilityPolicyInputResolver(UserProfileAccessQueryRepository repository) {
        this.repository = repository;
    }

    @Override
    public Class<? extends AuthorizationPolicy<ProfileVisibilityContext>> policyType() {
        return CanViewProfilePolicy.class;
    }

    @Override
    public Class<UserProfileAccessQuery> messageType() {
        return UserProfileAccessQuery.class;
    }

    @Override
    public ProfileVisibilityContext resolve(UserProfileAccessQuery message) {
        return repository.findVisibilityContextById(message.userProfileId())
                .orElseGet(ProfileVisibilityContext::missing);
    }
}

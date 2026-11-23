package uk.gov.justice.laa.datauserapi.application.query.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import uk.gov.justice.laa.datauserapi.application.query.dto.ProfileVisibilityContext;
import uk.gov.justice.laa.datauserapi.application.query.policy.CanViewProfileListPolicy;
import uk.gov.justice.laa.datauserapi.application.query.policy.ProfileVisibilityEvaluator;
import uk.gov.justice.laa.datauserapi.application.query.queryuserprofiles.GetUserProfilesQuery;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.UserProfileQueryRepository;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileSummaryView;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.security.rbac.AuthorizationPolicy;
import uk.gov.justice.laa.datauserapi.security.rbac.AuthorizationPolicyInputResolver;

import java.util.List;
import java.util.UUID;

/**
 * Loads, with a single read, the trusted profile-visibility facts for all profiles of a user
 * that {@link CanViewProfileListPolicy} evaluates. Also owns per-profile visibility filtering of
 * already-loaded profile summaries, so query services stay free of authorisation logic.
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

    /** Filters already-loaded profile summaries down to those visible to the actor. No DB read is performed. */
    public List<UserProfileSummaryView> filterVisible(List<UserProfileSummaryView> profiles, AuthenticatedEntraUser actor) {
        return profiles.stream()
                .filter(profile -> ProfileVisibilityEvaluator.isProfileVisible(actor, contextOf(profile)))
                .toList();
    }

    private static ProfileVisibilityContext contextOf(UserProfileSummaryView profile) {
        return new ProfileVisibilityContext(true, profile.userProfileId(), profile.userType(),
                profile.firmId() == null || profile.firmId().isBlank()
                        ? null : UUID.fromString(profile.firmId()));
    }
}

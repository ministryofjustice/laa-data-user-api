package uk.gov.justice.laa.datauserapi.application.query.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.query.mapper.UserProfileQueryMapper;
import uk.gov.justice.laa.datauserapi.application.query.policy.ProfileVisibilityEvaluator;
import uk.gov.justice.laa.datauserapi.application.query.queryactiveprofilebyentrauser.GetActiveProfileQueryMapper;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.UserProfileQueryRepository;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileDetailView;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileSummaryView;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.exception.UserProfileNotFoundException;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class UserProfileDetailsService {

    private final UserProfileQueryRepository repository;
    private final UserProfileQueryMapper queryMapper;
    private final GetActiveProfileQueryMapper viewMapper;
    private final UserProfileListVisibilityPolicyInputResolver listVisibilityResolver;

    public UserProfileDetailsService(UserProfileQueryRepository repository,
                                     UserProfileQueryMapper queryMapper,
                                     GetActiveProfileQueryMapper viewMapper,
                                     UserProfileListVisibilityPolicyInputResolver listVisibilityResolver) {
        this.repository = repository;
        this.queryMapper = queryMapper;
        this.viewMapper = viewMapper;
        this.listVisibilityResolver = listVisibilityResolver;
    }

    /**
     * Loads the actor's current active profile with one read and re-checks its visibility against the
     * actor before returning it. The RBAC interceptor already authorised the request against a separate,
     * earlier read of the same fact; re-checking here binds the final decision to the exact data returned,
     * so a profile switch between the two reads cannot leak a profile the actor isn't authorised to see.
     */
    public UserProfileDetailView getActiveProfile(String userEntraObjectId, AuthenticatedEntraUser actor) {
        UserProfileQueryMapper.ActiveProfileLookup lookup = queryMapper
                .toActiveProfileLookup(repository.findActiveProfileWithDetailsAndRoles(userEntraObjectId))
                .filter(result -> ProfileVisibilityEvaluator.isProfileVisible(actor, result.visibilityContext()))
                .orElseThrow(() -> UserProfileNotFoundException.activeProfileForUser(userEntraObjectId));

        return viewMapper.mapProfileToDetailView(lookup.profile());
    }

    public List<UserProfileSummaryView> getProfiles(String userEntraObjectId, AuthenticatedEntraUser actor) {
        List<UserProfileSummaryView> allProfiles = repository.findAllProfilesByUserEntraObjectId(userEntraObjectId)
                .orElse(List.of());

        return listVisibilityResolver.filterVisible(allProfiles, actor);
    }
}

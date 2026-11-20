package uk.gov.justice.laa.datauserapi.application.query.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.query.dto.ProfileVisibilityContext;
import uk.gov.justice.laa.datauserapi.application.query.mapper.UserProfileQueryMapper;
import uk.gov.justice.laa.datauserapi.application.query.policy.ProfileVisibilityEvaluator;
import uk.gov.justice.laa.datauserapi.application.query.queryactiveprofilebyentrauser.GetActiveProfileQueryMapper;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.UserProfileQueryRepository;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileDetailView;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileSummaryView;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.UserProfileDto;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class UserProfileDetailsService {

    private final UserProfileQueryRepository repository;
    private final UserProfileQueryMapper queryMapper;
    private final GetActiveProfileQueryMapper viewMapper;

    public UserProfileDetailsService(UserProfileQueryRepository repository,
                                     UserProfileQueryMapper queryMapper,
                                     GetActiveProfileQueryMapper viewMapper) {
        this.repository = repository;
        this.queryMapper = queryMapper;
        this.viewMapper = viewMapper;
    }

    public UserProfileDetailView getActiveProfile(String userEntraObjectId, UUID authorizedProfileId) {
        UserProfileDto targetProfile = queryMapper.toActiveProfile(
                repository.findProfileByIdWithDetailsAndRoles(userEntraObjectId, authorizedProfileId), userEntraObjectId);
        return viewMapper.mapProfileToDetailView(targetProfile);
    }

    public List<UserProfileSummaryView> getProfiles(String userEntraObjectId, AuthenticatedEntraUser actor) {
        List<UserProfileSummaryView> allProfiles = repository.findAllProfilesByUserEntraObjectId(userEntraObjectId)
                .orElse(List.of());
        
        if (allProfiles.isEmpty()) {
            return List.of();
        }

        return allProfiles.stream()
                .filter(profile -> {
                    ProfileVisibilityContext context = buildContextFromSummary(profile);
                    return ProfileVisibilityEvaluator.isProfileVisible(actor, context);
                })
                .collect(Collectors.toList());
    }

    private ProfileVisibilityContext buildContextFromSummary(UserProfileSummaryView profile) {
        return new ProfileVisibilityContext(true, profile.userProfileId(), profile.userType(),
                UUID.fromString(profile.firmId()));
    }
}

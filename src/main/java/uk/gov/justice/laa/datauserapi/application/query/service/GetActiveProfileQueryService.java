package uk.gov.justice.laa.datauserapi.application.query.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.query.mapper.GetActiveProfileQueryMapper;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.UserProfileQueryRepository;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileDetailView;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.exception.UserProfileNotFoundException;

@Service
@Transactional(readOnly = true)
public class GetActiveProfileQueryService {

    private final UserProfileQueryRepository userProfileQueryRepository;
    private final GetActiveProfileQueryMapper getActiveProfileQueryMapper;

    public GetActiveProfileQueryService(UserProfileQueryRepository userProfileQueryRepository, GetActiveProfileQueryMapper getActiveProfileQueryMapper) {
        this.userProfileQueryRepository = userProfileQueryRepository;
        this.getActiveProfileQueryMapper = getActiveProfileQueryMapper;
    }

    public UserProfileDetailView getUserActiveProfile(String userEntraObjectId) {
        UserProfile userProfile = userProfileQueryRepository
                .findActiveProfileWithDetailsAndRoles(userEntraObjectId)
                .orElseThrow(() -> new UserProfileNotFoundException(
                        "Active profile not found for user: " + userEntraObjectId));
        return getActiveProfileQueryMapper.mapProfileToDetailView(userProfile);

    }
}


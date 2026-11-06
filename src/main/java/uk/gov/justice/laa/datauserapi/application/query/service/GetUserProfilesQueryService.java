package uk.gov.justice.laa.datauserapi.application.query.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.UserProfileQueryRepository;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileSummaryView;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class GetUserProfilesQueryService {

    private final UserProfileQueryRepository userProfileQueryRepository;

    public GetUserProfilesQueryService(UserProfileQueryRepository userProfileQueryRepository) {
        this.userProfileQueryRepository = userProfileQueryRepository;
    }

    public List<UserProfileSummaryView> getUserProfiles(String userEntraObjectId) {
        return userProfileQueryRepository
                .findAllProfilesByUserEntraObjectId(userEntraObjectId)
                .orElse(List.of());
    }
}

package uk.gov.justice.laa.datauserapi.application.query.getuserprofiles;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.UserProfileReadRepository;
import uk.gov.justice.laa.datauserapi.application.query.getuserprofiles.readmodel.GetUserProfilesReadModel;
import uk.gov.justice.laa.datauserapi.application.query.shared.PagedReadModel;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.shared.QueryHandler;

@Component
@RequiredArgsConstructor
public class GetUserProfilesHandler implements QueryHandler<GetUserProfilesQuery, PagedReadModel<GetUserProfilesReadModel>> {
    private final UserProfileReadRepository userProfileReadRepository;
    private final GetUserProfilesReadMapper getUserProfilesReadMapper;

    @Override
    public PagedReadModel<GetUserProfilesReadModel> handle(GetUserProfilesQuery getUserProfilesQuery) {
        Page<UserProfile> userProfilePage = userProfileReadRepository.findAll(getUserProfilesQuery, getUserProfilesQuery.pageRequest());
        return getUserProfilesReadMapper.map(userProfilePage);
    }
}

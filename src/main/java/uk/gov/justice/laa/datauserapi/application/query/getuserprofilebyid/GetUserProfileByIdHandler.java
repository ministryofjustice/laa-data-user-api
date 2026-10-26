package uk.gov.justice.laa.datauserapi.application.query.getuserprofilebyid;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.datauserapi.application.query.getuserprofilebyid.readmodel.GetUserProfileByIdReadModel;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.UserProfileReadRepository;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.exception.ResourceNotFoundException;
import uk.gov.justice.laa.datauserapi.shared.QueryHandler;

@Component
@RequiredArgsConstructor
public class GetUserProfileByIdHandler implements QueryHandler<GetUserProfileByIdQuery, GetUserProfileByIdReadModel> {

    private final UserProfileReadRepository userProfileReadRepository;
    private final GetUserProfileByIdReadMapper readMapper;

    @Override
    public GetUserProfileByIdReadModel handle(GetUserProfileByIdQuery query) {
        UserProfile userProfile = userProfileReadRepository.findById(query)
                .orElseThrow(() -> new ResourceNotFoundException("Could not find user profile with id: " + query.id()));
        return readMapper.map(userProfile);
    }

}

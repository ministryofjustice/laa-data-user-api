package uk.gov.justice.laa.datauserapi.application.query.handler;


import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.query.queryactiveprofilebyentrauser.GetActiveProfileQuery;
import uk.gov.justice.laa.datauserapi.application.query.queryactiveprofilebyentrauser.GetActiveProfileQueryMapper;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.UserProfileQueryRepository;
import uk.gov.justice.laa.datauserapi.contracts.dto.UserProfileDetailView;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.exception.ResourceNotFoundException;


@Component
@Transactional(readOnly = true)
public class GetActiveProfileHandler {

    private final UserProfileQueryRepository userProfileQueryRepository;

    public GetActiveProfileHandler(UserProfileQueryRepository userProfileQueryRepository) {
        this.userProfileQueryRepository = userProfileQueryRepository;
    }

    public UserProfileDetailView handle(GetActiveProfileQuery query) {
        UserProfile activeProfile =
                userProfileQueryRepository.findActiveProfileWithDetailsAndRoles(query.userEntraObjectId(), query.actorOid())
                .orElseThrow(() -> new ResourceNotFoundException("Active profile not found for user: " + query.userEntraObjectId()));
        return new GetActiveProfileQueryMapper().mapProfileToDetailView(activeProfile);
    }
}

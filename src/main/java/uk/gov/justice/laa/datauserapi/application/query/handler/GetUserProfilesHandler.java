package uk.gov.justice.laa.datauserapi.application.query.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import uk.gov.justice.laa.datauserapi.application.query.queryuserprofilesbyentrauser.GetUserProfilesQuery;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.UserProfileQueryRepository;
import uk.gov.justice.laa.datauserapi.contracts.dto.UserProfileSummaryView;

import java.util.List;

@Component
@Transactional(readOnly = true)
public class GetUserProfilesHandler {

    private final UserProfileQueryRepository userProfileQueryRepository;

    public GetUserProfilesHandler(UserProfileQueryRepository userProfileQueryRepository) {
        this.userProfileQueryRepository = userProfileQueryRepository;
    }

    public List<UserProfileSummaryView> handle(GetUserProfilesQuery query) {
        return userProfileQueryRepository.findAllProfilesByUserEntraObjectId(query.userEntraObjectId(), query.actorOid());
    }
}

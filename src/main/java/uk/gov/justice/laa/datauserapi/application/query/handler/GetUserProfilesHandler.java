package uk.gov.justice.laa.datauserapi.application.query.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import uk.gov.justice.laa.datauserapi.application.query.queryprofile.GetUserProfilesQuery;
import uk.gov.justice.laa.datauserapi.application.query.service.GetUserProfilesQueryService;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileSummaryView;

import java.util.List;

@Component
@Transactional(readOnly = true)
public class GetUserProfilesHandler {

    private final GetUserProfilesQueryService getUserProfilesQueryService;

    public GetUserProfilesHandler(GetUserProfilesQueryService getUserProfilesQueryService) {
        this.getUserProfilesQueryService = getUserProfilesQueryService;
    }

    public List<UserProfileSummaryView> handle(GetUserProfilesQuery query) {
        return getUserProfilesQueryService.getUserProfiles(query.userEntraObjectId());
    }
}

package uk.gov.justice.laa.datauserapi.application.query.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import uk.gov.justice.laa.datauserapi.application.query.queryuserprofileaccess.GetUserProfileAppsQuery;
import uk.gov.justice.laa.datauserapi.application.query.service.UserProfileAccessQueryService;
import uk.gov.justice.laa.datauserapi.contracts.response.AppViewList;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;

@Component
@Transactional(readOnly = true)
public class GetUserProfileAppsHandler implements QueryHandler<GetUserProfileAppsQuery, AppViewList> {

    private final UserProfileAccessQueryService service;

    public GetUserProfileAppsHandler(UserProfileAccessQueryService service) {
        this.service = service;
    }

    @Override
    public AppViewList handle(GetUserProfileAppsQuery query, AuthenticatedEntraUser actor) {
        return service.getApps(query.userProfileId());
    }
}

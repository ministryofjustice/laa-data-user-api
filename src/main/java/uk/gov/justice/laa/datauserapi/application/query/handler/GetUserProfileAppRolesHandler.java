package uk.gov.justice.laa.datauserapi.application.query.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import uk.gov.justice.laa.datauserapi.application.query.queryuserprofileaccess.GetUserProfileAppRolesQuery;
import uk.gov.justice.laa.datauserapi.application.query.service.UserProfileAccessQueryService;
import uk.gov.justice.laa.datauserapi.contracts.response.AppRoleViewList;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;

@Component
@Transactional(readOnly = true)
public class GetUserProfileAppRolesHandler implements QueryHandler<GetUserProfileAppRolesQuery, AppRoleViewList> {

    private final UserProfileAccessQueryService service;

    public GetUserProfileAppRolesHandler(UserProfileAccessQueryService service) {
        this.service = service;
    }

    @Override
    public AppRoleViewList handle(GetUserProfileAppRolesQuery query, AuthenticatedEntraUser actor) {
        return service.getAppRoles(query.userProfileId(), query.appId(), query.appEntraClientId());
    }
}

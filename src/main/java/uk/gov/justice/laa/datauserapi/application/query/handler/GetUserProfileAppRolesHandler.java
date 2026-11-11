package uk.gov.justice.laa.datauserapi.application.query.handler;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import uk.gov.justice.laa.datauserapi.application.query.service.UserProfileAccessQueryService;
import uk.gov.justice.laa.datauserapi.contracts.response.AppRoleViewList;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;

@Component
@Transactional(readOnly = true)
public class GetUserProfileAppRolesHandler {

    private final UserProfileAccessQueryService service;

    public GetUserProfileAppRolesHandler(UserProfileAccessQueryService service) {
        this.service = service;
    }

    public AppRoleViewList handle(UUID userProfileId, UUID appId, UUID appEntraClientId,
                                  AuthenticatedEntraUser actor) {
        return service.getAppRoles(userProfileId, appId, appEntraClientId, actor);
    }
}

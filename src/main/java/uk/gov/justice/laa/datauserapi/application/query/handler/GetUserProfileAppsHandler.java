package uk.gov.justice.laa.datauserapi.application.query.handler;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import uk.gov.justice.laa.datauserapi.application.query.service.UserProfileAccessQueryService;
import uk.gov.justice.laa.datauserapi.contracts.response.AppViewList;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;

@Component
@Transactional(readOnly = true)
public class GetUserProfileAppsHandler {

    private final UserProfileAccessQueryService service;

    public GetUserProfileAppsHandler(UserProfileAccessQueryService service) {
        this.service = service;
    }

    public AppViewList handle(UUID userProfileId, AuthenticatedEntraUser actor) {
        return service.getApps(userProfileId, actor);
    }
}

package uk.gov.justice.laa.datauserapi.application.query.handler;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import uk.gov.justice.laa.datauserapi.application.query.dto.OfficeViewList;
import uk.gov.justice.laa.datauserapi.application.query.service.UserProfileAccessQueryService;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;

@Component
@Transactional(readOnly = true)
public class GetUserProfileOfficesHandler {

    private final UserProfileAccessQueryService service;

    public GetUserProfileOfficesHandler(UserProfileAccessQueryService service) {
        this.service = service;
    }

    public OfficeViewList handle(UUID userProfileId, AuthenticatedEntraUser actor) {
        return service.getOffices(userProfileId, actor);
    }
}

package uk.gov.justice.laa.datauserapi.application.query.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.query.service.UserProfileDetailsService;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileSummaryView;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;

import java.util.List;

@Component
@Transactional(readOnly = true)
public class GetUserProfilesHandler {

    private final UserProfileDetailsService service;

    public GetUserProfilesHandler(UserProfileDetailsService service) {
        this.service = service;
    }

    public List<UserProfileSummaryView> handle(String userEntraObjectId, AuthenticatedEntraUser authenticatedUser) {
        return service.getProfiles(userEntraObjectId, authenticatedUser);
    }
}

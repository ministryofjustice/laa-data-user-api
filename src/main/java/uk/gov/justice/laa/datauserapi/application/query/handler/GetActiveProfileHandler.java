package uk.gov.justice.laa.datauserapi.application.query.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.query.service.UserProfileDetailsService;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileDetailView;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;

@Component
@Transactional(readOnly = true)
public class GetActiveProfileHandler {

    private final UserProfileDetailsService service;

    public GetActiveProfileHandler(UserProfileDetailsService service) {
        this.service = service;
    }

    public UserProfileDetailView handle(String userEntraObjectId, AuthenticatedEntraUser authenticatedUser) {
        return service.getActiveProfile(userEntraObjectId, authenticatedUser);
    }
}

package uk.gov.justice.laa.datauserapi.application.query.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import uk.gov.justice.laa.datauserapi.application.query.queryuserprofiles.GetActiveProfileQuery;
import uk.gov.justice.laa.datauserapi.application.query.service.UserProfileDetailsService;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileDetailView;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;

@Component
@Transactional(readOnly = true)
public class GetActiveProfileHandler implements QueryHandler<GetActiveProfileQuery, UserProfileDetailView> {

    private final UserProfileDetailsService service;

    public GetActiveProfileHandler(UserProfileDetailsService service) {
        this.service = service;
    }

    @Override
    public UserProfileDetailView handle(GetActiveProfileQuery query, AuthenticatedEntraUser actor) {
        return service.getActiveProfile(query.userEntraObjectId());
    }
}

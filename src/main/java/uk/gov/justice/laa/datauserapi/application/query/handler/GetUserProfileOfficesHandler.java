package uk.gov.justice.laa.datauserapi.application.query.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import uk.gov.justice.laa.datauserapi.application.query.dto.OfficeViewList;
import uk.gov.justice.laa.datauserapi.application.query.queryuserprofileaccess.GetUserProfileOfficesQuery;
import uk.gov.justice.laa.datauserapi.application.query.service.UserProfileAccessQueryService;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;

@Component
@Transactional(readOnly = true)
public class GetUserProfileOfficesHandler implements QueryHandler<GetUserProfileOfficesQuery, OfficeViewList> {

    private final UserProfileAccessQueryService service;

    public GetUserProfileOfficesHandler(UserProfileAccessQueryService service) {
        this.service = service;
    }

    @Override
    public OfficeViewList handle(GetUserProfileOfficesQuery query, AuthenticatedEntraUser actor) {
        return service.getOffices(query.userProfileId());
    }
}

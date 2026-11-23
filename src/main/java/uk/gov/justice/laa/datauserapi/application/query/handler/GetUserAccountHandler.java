package uk.gov.justice.laa.datauserapi.application.query.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import uk.gov.justice.laa.datauserapi.application.query.queryuserbyid.GetUserAccountQuery;
import uk.gov.justice.laa.datauserapi.application.query.service.UserAccountDetailsService;
import uk.gov.justice.laa.datauserapi.contracts.response.UserView;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;

@Component
@Transactional(readOnly = true)
public class GetUserAccountHandler implements QueryHandler<GetUserAccountQuery, UserView> {

    private final UserAccountDetailsService service;

    public GetUserAccountHandler(UserAccountDetailsService service) {
        this.service = service;
    }

    @Override
    public UserView handle(GetUserAccountQuery query, AuthenticatedEntraUser actor) {
        return service.getAccount(query.userEntraObjectId(), actor.entraOid());
    }
}
package uk.gov.justice.laa.datauserapi.application.query.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.query.queryuserbyid.GetUserAccountQuery;
import uk.gov.justice.laa.datauserapi.application.query.service.UserAccountDetailsService;
import uk.gov.justice.laa.datauserapi.contracts.response.UserView;

@Component
@Transactional(readOnly = true)
public class GetUserAccountHandler {

    private final UserAccountDetailsService service;

    public GetUserAccountHandler(UserAccountDetailsService service) {
        this.service = service;
    }

    public UserView handle(GetUserAccountQuery query) {
        return service.getAccount(query);
    }
}

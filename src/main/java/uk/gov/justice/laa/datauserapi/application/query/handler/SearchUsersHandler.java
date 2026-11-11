package uk.gov.justice.laa.datauserapi.application.query.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.query.queryusersearch.SearchUsersQuery;
import uk.gov.justice.laa.datauserapi.application.query.service.UserSearchService;
import uk.gov.justice.laa.datauserapi.contracts.response.UserAccountSummaryPage;

@Component
@Transactional(readOnly = true)
public class SearchUsersHandler {

    private final UserSearchService service;

    public SearchUsersHandler(UserSearchService service) {
        this.service = service;
    }

    public UserAccountSummaryPage handle(SearchUsersQuery query) {
        return service.search(query);
    }
}

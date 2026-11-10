package uk.gov.justice.laa.datauserapi.application.query.handler;

import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.datauserapi.application.query.queryusersearch.SearchUsersQuery;
import uk.gov.justice.laa.datauserapi.application.query.service.UserSearchService;
import uk.gov.justice.laa.datauserapi.contracts.response.UserAccountSummaryPage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SearchUsersHandlerTest {

    @Test
    void delegatesQueryAndPreservesResult() {
        UserSearchService service = mock(UserSearchService.class);
        SearchUsersQuery query = new SearchUsersQuery(null, "actor");
        UserAccountSummaryPage expected = mock(UserAccountSummaryPage.class);
        when(service.search(query)).thenReturn(expected);

        assertThat(new SearchUsersHandler(service).handle(query)).isSameAs(expected);
        verify(service).search(query);
    }
}

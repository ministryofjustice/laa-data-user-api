package uk.gov.justice.laa.datauserapi.application.query.handler;

import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.datauserapi.application.query.queryuserbyid.GetUserAccountQuery;
import uk.gov.justice.laa.datauserapi.application.query.service.UserAccountDetailsService;
import uk.gov.justice.laa.datauserapi.contracts.response.UserView;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GetUserAccountHandlerTest {

    @Test
    void delegatesQueryAndPreservesResult() {
        UserAccountDetailsService service = mock(UserAccountDetailsService.class);
        GetUserAccountQuery query = new GetUserAccountQuery("target");
        String actorOid = "actor";
        AuthenticatedEntraUser actor = AuthenticatedEntraUser.builder().entraOid(actorOid).build();
        UserView expected = mock(UserView.class);
        when(service.getAccount("target", actorOid)).thenReturn(expected);

        assertThat(new GetUserAccountHandler(service).handle(query, actor)).isSameAs(expected);
        verify(service).getAccount("target", actorOid);
    }
}


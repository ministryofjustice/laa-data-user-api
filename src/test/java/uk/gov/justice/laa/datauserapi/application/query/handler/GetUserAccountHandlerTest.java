package uk.gov.justice.laa.datauserapi.application.query.handler;

import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.datauserapi.application.query.queryuserbyid.GetUserAccountQuery;
import uk.gov.justice.laa.datauserapi.application.query.service.UserAccountDetailsService;
import uk.gov.justice.laa.datauserapi.contracts.response.UserView;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.exception.InvalidUuidFormatException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GetUserAccountHandlerTest {

    @Test
    void delegatesQueryAndPreservesResult() {
        UserAccountDetailsService service = mock(UserAccountDetailsService.class);
        GetUserAccountQuery query = new GetUserAccountQuery("1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d");
        String actorOid = "actor";
        AuthenticatedEntraUser actor = AuthenticatedEntraUser.builder().entraOid(actorOid).build();
        UserView expected = mock(UserView.class);
        when(service.getAccount("1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d", actorOid)).thenReturn(expected);

        assertThat(new GetUserAccountHandler(service).handle(query, actor)).isSameAs(expected);
        verify(service).getAccount("1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d", actorOid);
    }

    @Test
    void throwsForMalformedUuid() {
        assertThatThrownBy(() -> new GetUserAccountQuery("invalid-uuid"))
                .isInstanceOf(InvalidUuidFormatException.class)
                .hasMessageContaining("userEntraObjectId must match UUID format");
    }
}


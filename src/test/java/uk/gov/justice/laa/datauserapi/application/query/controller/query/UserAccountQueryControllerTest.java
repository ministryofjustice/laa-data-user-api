package uk.gov.justice.laa.datauserapi.application.query.controller.query;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import uk.gov.justice.laa.datauserapi.application.query.controller.UserAccountQueryController;
import uk.gov.justice.laa.datauserapi.contracts.dto.UserSearchCriteria;
import uk.gov.justice.laa.datauserapi.contracts.dto.PageMetadata;
import uk.gov.justice.laa.datauserapi.contracts.dto.UserAccountSummaryPage;
import uk.gov.justice.laa.datauserapi.contracts.dto.UserView;
import uk.gov.justice.laa.datauserapi.application.query.handler.GetUserAccountHandler;
import uk.gov.justice.laa.datauserapi.application.query.handler.SearchUsersHandler;
import uk.gov.justice.laa.datauserapi.application.query.queryuserbyid.GetUserAccountQuery;
import uk.gov.justice.laa.datauserapi.application.query.queryusersearch.SearchUsersQuery;
import uk.gov.justice.laa.datauserapi.application.query.service.UserAccountQueryService;
import uk.gov.justice.laa.datauserapi.contracts.response.UserAccountSummaryViewResponse;
import uk.gov.justice.laa.datauserapi.contracts.response.UserViewResponse;


import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAccountQueryControllerTest {

    private UserAccountQueryController controller;

    @Mock
    UserAccountQueryService queryService;

    @Mock
    private SearchUsersHandler searchUsersHandler;

    @Mock
    private GetUserAccountHandler getUserAccountHandler;


    private Jwt jwt;

    @BeforeEach
    void setUp() {
        controller = new UserAccountQueryController(
                queryService,
                searchUsersHandler,
                getUserAccountHandler);

        jwt = mock(Jwt.class);
    }

    @Test
    void searchUsers_whenValidRequest_shouldCallHandler() {
        var page = new UserAccountSummaryPage(
                null,
                new PageMetadata(0, 20, 0, 0));

        String actorOid = "actor-oid-123";

        UserSearchCriteria criteria = new UserSearchCriteria(
                0, 20, null, null, null, null, null, null,
                null, null, null, true, false, null, false);

        when(jwt.getClaimAsString("oid")).thenReturn(actorOid);
        when(searchUsersHandler.handle(any(SearchUsersQuery.class)))
                .thenReturn(page);

        ResponseEntity<UserAccountSummaryViewResponse> result =
                controller.searchUsers(criteria, jwt);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());

        ArgumentCaptor<SearchUsersQuery> captor =
                ArgumentCaptor.forClass(SearchUsersQuery.class);

        verify(searchUsersHandler).handle(captor.capture());

        assertEquals(criteria, captor.getValue().criteria());
        assertEquals(actorOid, captor.getValue().actorOid());

        UserAccountSummaryViewResponse expected =
                new UserAccountSummaryViewResponse(page.items(), page.page());

        assertEquals(expected, result.getBody());

    }

    @Test
    void getUserAccount_whenValidRequest_shouldCallHandler() {
        String userEntraObjectId = "123e4567-e89b-12d3-a456-426614174000";
        String actorOid = "actor-oid-123";

        UserView userView = new UserView(null, null);

        when(jwt.getClaimAsString("oid")).thenReturn(actorOid);
        when(getUserAccountHandler.handle(any(GetUserAccountQuery.class)))
                .thenReturn(userView);

        ResponseEntity<UserViewResponse> result = controller.getUserAccount(userEntraObjectId, jwt);

        verify(getUserAccountHandler).handle(any(GetUserAccountQuery.class));

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());

        UserViewResponse expected = new UserViewResponse(null, null);

        assertEquals(expected, result.getBody());
    }
}

package uk.gov.justice.laa.datauserapi.application.query.controller.query;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import uk.gov.justice.laa.datauserapi.application.query.controller.UserAccountQueryController;
import uk.gov.justice.laa.datauserapi.application.query.dto.PageMetadata;
import uk.gov.justice.laa.datauserapi.application.query.dto.UserAccountSummaryPage;
import uk.gov.justice.laa.datauserapi.application.query.dto.UserSearchCriteria;
import uk.gov.justice.laa.datauserapi.application.query.dto.UserView;
import uk.gov.justice.laa.datauserapi.application.query.handler.GetUserAccountHandler;
import uk.gov.justice.laa.datauserapi.application.query.handler.SearchUsersHandler;
import uk.gov.justice.laa.datauserapi.application.query.queryuserbyid.GetUserAccountQuery;
import uk.gov.justice.laa.datauserapi.application.query.queryusersearch.SearchUsersQuery;
import uk.gov.justice.laa.datauserapi.application.query.service.UserAccountQueryService;


import static org.junit.Assert.assertEquals;
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
        UserSearchCriteria criteria = new UserSearchCriteria(
                0, 20, null, null, null, null, null, null,
                null, null, null, true, false, null, false);

        var page = new UserAccountSummaryPage(
                null,
                new PageMetadata(0, 20, 0, 0));

        String actorOid = "actor-oid-123";

        when(jwt.getClaimAsString("oid")).thenReturn(actorOid);
        when(searchUsersHandler.handle(any(SearchUsersQuery.class)))
                .thenReturn(page);

        ResponseEntity<UserAccountSummaryPage> result =
                controller.searchUsers(criteria, jwt);

        verify(searchUsersHandler).handle(any(SearchUsersQuery.class));

        assertEquals(page, result.getBody());
    }

    @Test
    void getUserAccount_whenValidRequest_shouldCallHandler() {
        String userEntraObjectId = "123e4567-e89b-12d3-a456-426614174000";

        String actorOid = "actor-oid-123";

        UserView userView = new UserView(null, null);

        when(jwt.getClaimAsString("oid")).thenReturn(actorOid);
        when(getUserAccountHandler.handle(any(GetUserAccountQuery.class)))
                .thenReturn(userView);

        ResponseEntity<UserView> result =
                controller.getUserAccount(userEntraObjectId, jwt);

        verify(getUserAccountHandler)
                .handle(any(GetUserAccountQuery.class));

        assertEquals(userView, result.getBody());
    }
}

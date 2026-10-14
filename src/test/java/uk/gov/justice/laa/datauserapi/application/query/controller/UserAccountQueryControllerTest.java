package uk.gov.justice.laa.datauserapi.application.query.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import uk.gov.justice.laa.datauserapi.application.query.handler.GetActiveProfileHandler;
import uk.gov.justice.laa.datauserapi.application.query.handler.GetUserProfilesHandler;
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
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;


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

    @Mock
    private GetUserProfilesHandler getUserProfilesHandler;

    @Mock
    private GetActiveProfileHandler getActiveProfileHandler;

    private Jwt jwt;

    private static final String ACTOR_OID = "actor-oid-123";
    private static final String USER_ENTRA_OBJECT_ID = "123e4567-e89b-12d3-a456-426614174000";

    @BeforeEach
    void setUp() {
        controller =  new UserAccountQueryController(
                queryService,
                searchUsersHandler,
                getUserAccountHandler,
                getUserProfilesHandler,
                getActiveProfileHandler);

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

        AuthenticatedEntraUser user = AuthenticatedEntraUser.builder()
                .entraOid(actorOid)
                .build();
        when(searchUsersHandler.handle(any(SearchUsersQuery.class)))
                .thenReturn(page);

        ResponseEntity<UserAccountSummaryViewResponse> result =
                controller.searchUsers(criteria, user);

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
        UserView userView = new UserView(null, null);

        AuthenticatedEntraUser user = AuthenticatedEntraUser.builder()
                .entraOid(actorOid)
                .build();
        when(jwt.getClaimAsString("oid")).thenReturn(ACTOR_OID);
        when(getUserAccountHandler.handle(any(GetUserAccountQuery.class)))
                .thenReturn(userView);

        ResponseEntity<UserViewResponse> result = controller.getUserAccount(USER_ENTRA_OBJECT_ID, jwt);
        verify(getUserAccountHandler).handle(any(GetUserAccountQuery.class));
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        UserViewResponse expected = new UserViewResponse(null, null);
        assertEquals(expected, result.getBody());
    }

    @Test
    void getUserProfiles_whenValidRequest_shouldCallHandler() {
        when(jwt.getClaimAsString("oid")).thenReturn(ACTOR_OID);
        when(getUserProfilesHandler.handle(any()))
                .thenReturn(null);

        ResponseEntity<?> result = controller.getUserProfiles(USER_ENTRA_OBJECT_ID, jwt);
        verify(getUserProfilesHandler).handle(any());
        assertEquals(HttpStatus.OK, result.getStatusCode());
    }

    @Test
    void getActiveProfile_whenValidRequest_shouldCallHandler() {
        when(jwt.getClaimAsString("oid")).thenReturn(ACTOR_OID);
        when(getActiveProfileHandler.handle(any()))
                .thenReturn(null);

        ResponseEntity<?> result = controller.getActiveProfile(USER_ENTRA_OBJECT_ID, jwt);
        verify(getActiveProfileHandler).handle(any());
        assertEquals(HttpStatus.OK, result.getStatusCode());
    }
}

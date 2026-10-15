package uk.gov.justice.laa.datauserapi.application.query.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import uk.gov.justice.laa.datauserapi.application.query.handler.GetActiveProfileHandler;
import uk.gov.justice.laa.datauserapi.application.query.handler.GetUserProfilesHandler;
import uk.gov.justice.laa.datauserapi.contracts.dto.UserSearchCriteria;
import uk.gov.justice.laa.datauserapi.contracts.dto.PageMetadata;
import uk.gov.justice.laa.datauserapi.contracts.response.UserAccountSummaryPage;
import uk.gov.justice.laa.datauserapi.contracts.response.UserView;
import uk.gov.justice.laa.datauserapi.application.query.handler.GetUserAccountHandler;
import uk.gov.justice.laa.datauserapi.application.query.handler.SearchUsersHandler;
import uk.gov.justice.laa.datauserapi.application.query.queryuserbyid.GetUserAccountQuery;
import uk.gov.justice.laa.datauserapi.application.query.queryusersearch.SearchUsersQuery;
import uk.gov.justice.laa.datauserapi.application.query.service.UserAccountQueryService;
import uk.gov.justice.laa.datauserapi.contracts.response.UserAccountSummaryPage;
import uk.gov.justice.laa.datauserapi.contracts.response.UserView;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;


import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
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

    }

    @Test
    void searchUsers_whenValidRequest_shouldCallHandler() {
        var page = new UserAccountSummaryPage(
                null,
                new PageMetadata(0, 20, 0, 0));


        UserSearchCriteria criteria = new UserSearchCriteria(
                0, 20, null, null, null, null, null, null,
                null, null, null, true, false, null, false);

        AuthenticatedEntraUser user = AuthenticatedEntraUser.builder()
                .entraOid(ACTOR_OID)
                .build();
        when(searchUsersHandler.handle(any(SearchUsersQuery.class)))
                .thenReturn(page);

        ResponseEntity<UserAccountSummaryPage> result =
                controller.searchUsers(criteria, user);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());

        ArgumentCaptor<SearchUsersQuery> captor =
                ArgumentCaptor.forClass(SearchUsersQuery.class);

        verify(searchUsersHandler).handle(captor.capture());
        assertEquals(criteria, captor.getValue().criteria());
        assertEquals(ACTOR_OID, captor.getValue().actorOid());

        UserAccountSummaryPage expected =
                new UserAccountSummaryPage(page.items(), page.page());
        assertEquals(expected, result.getBody());

    }

    @Test
    void getUserAccount_whenValidRequest_shouldCallHandler() {
        UserView userView = new UserView(null, null);

        AuthenticatedEntraUser user = AuthenticatedEntraUser.builder()
                .entraOid(ACTOR_OID)
                .build();
        when(getUserAccountHandler.handle(any(GetUserAccountQuery.class)))
                .thenReturn(userView);

        ResponseEntity<UserView> result = controller.getUserAccount(USER_ENTRA_OBJECT_ID, user);
        verify(getUserAccountHandler).handle(any(GetUserAccountQuery.class));
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        UserView expected = new UserView(null, null);
        assertEquals(expected, result.getBody());
    }

    @Test
    void getUserProfiles_whenValidRequest_shouldCallHandler() {
        AuthenticatedEntraUser user = AuthenticatedEntraUser.builder()
                .entraOid(ACTOR_OID)
                .build();
        when(getUserProfilesHandler.handle(any()))
                .thenReturn(null);

        ResponseEntity<?> result = controller.getUserProfiles(USER_ENTRA_OBJECT_ID, user);
        verify(getUserProfilesHandler).handle(any());
        assertEquals(HttpStatus.OK, result.getStatusCode());
    }

    @Test
    void getActiveProfile_whenValidRequest_shouldCallHandler() {
        AuthenticatedEntraUser user = AuthenticatedEntraUser.builder()
                .entraOid(ACTOR_OID)
                .build();
        when(getActiveProfileHandler.handle(any()))
                .thenReturn(null);

        ResponseEntity<?> result = controller.getActiveProfile(USER_ENTRA_OBJECT_ID, user);
        verify(getActiveProfileHandler).handle(any());
        assertEquals(HttpStatus.OK, result.getStatusCode());
    }
}

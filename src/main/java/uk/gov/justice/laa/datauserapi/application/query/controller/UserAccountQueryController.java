package uk.gov.justice.laa.datauserapi.application.query.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.datauserapi.application.query.dto.AccountStatusHistoryView;
import uk.gov.justice.laa.datauserapi.application.query.dto.UserAccountStatusView;
import uk.gov.justice.laa.datauserapi.application.query.handler.GetActiveProfileHandler;
import uk.gov.justice.laa.datauserapi.application.query.handler.GetUserProfilesHandler;
import uk.gov.justice.laa.datauserapi.contracts.dto.UserSearchCriteria;
import uk.gov.justice.laa.datauserapi.application.query.handler.GetUserAccountHandler;
import uk.gov.justice.laa.datauserapi.application.query.queryuserbyid.GetUserAccountQuery;
import uk.gov.justice.laa.datauserapi.application.query.handler.SearchUsersHandler;
import uk.gov.justice.laa.datauserapi.application.query.queryusersearch.SearchUsersQuery;
import uk.gov.justice.laa.datauserapi.application.query.service.UserAccountQueryService;
import uk.gov.justice.laa.datauserapi.contracts.response.UserAccountSummaryPage;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileDetailView;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileSummaryView;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileSummaryList;
import uk.gov.justice.laa.datauserapi.contracts.response.UserView;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.security.RequiresReadScope;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/queries")
@RequiresReadScope
public class UserAccountQueryController {

    private final UserAccountQueryService queryService;
    private final SearchUsersHandler searchUsersHandler;
    private final GetUserAccountHandler getUserAccountHandler;
    private final GetUserProfilesHandler getUserProfilesHandler;
    private final GetActiveProfileHandler getActiveProfileHandler;

    public UserAccountQueryController(UserAccountQueryService queryService, SearchUsersHandler searchUsersHandler,
                                      GetUserAccountHandler getUserAccountHandler,
                                      GetUserProfilesHandler getUserProfilesHandler,
                                      GetActiveProfileHandler getActiveProfileHandler) {
        this.queryService = queryService;
        this.searchUsersHandler = searchUsersHandler;
        this.getUserAccountHandler = getUserAccountHandler;
        this.getUserProfilesHandler = getUserProfilesHandler;
        this.getActiveProfileHandler = getActiveProfileHandler;
    }

    @GetMapping("/users/{userEntraObjectId}/status")
    public ResponseEntity<UserAccountStatusView> getUserStatus(@PathVariable UUID userEntraObjectId) {
        return ResponseEntity.ok(queryService.getUserStatus(userEntraObjectId));
    }

    @GetMapping("/audit/users/{userEntraObjectId}/history")
    public ResponseEntity<List<AccountStatusHistoryView>> getAuditHistory(@PathVariable UUID userEntraObjectId) {
        return ResponseEntity.ok(queryService.getAuditHistory(userEntraObjectId));
    }

    @GetMapping("/users")
    public ResponseEntity<UserAccountSummaryPage> searchUsers(
            @Valid @ModelAttribute UserSearchCriteria criteria, @AuthenticationPrincipal AuthenticatedEntraUser authenticatedUser) {
        UserAccountSummaryPage readModel = searchUsersHandler.handle(new SearchUsersQuery(criteria, authenticatedUser.entraOid()));
        return ResponseEntity.ok(readModel);
    }

    @GetMapping("/users/{userEntraObjectId}")
    public ResponseEntity<UserView> getUserAccount(
            @PathVariable String userEntraObjectId, @AuthenticationPrincipal AuthenticatedEntraUser authenticatedUser) {
        UserView readModel = getUserAccountHandler.handle(new GetUserAccountQuery(userEntraObjectId, authenticatedUser.entraOid()));
        return ResponseEntity.ok(readModel);
    }

    @GetMapping("/users/{userEntraObjectId}/profiles")
    public ResponseEntity<UserProfileSummaryList> getUserProfiles(@PathVariable String userEntraObjectId,
                                                                          @AuthenticationPrincipal AuthenticatedEntraUser authenticatedUser) {
        List<UserProfileSummaryView> readModel = getUserProfilesHandler.handle(userEntraObjectId, authenticatedUser);
        return ResponseEntity.ok(new UserProfileSummaryList(readModel));
    }

    @GetMapping("/users/{userEntraObjectId}/active-profile")
    public ResponseEntity<UserProfileDetailView> getActiveProfile(@PathVariable String userEntraObjectId,
                                                                   @AuthenticationPrincipal AuthenticatedEntraUser authenticatedUser) {
        UserProfileDetailView readModel = getActiveProfileHandler.handle(userEntraObjectId, authenticatedUser);
        return ResponseEntity.ok(readModel);
    }
}

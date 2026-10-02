package uk.gov.justice.laa.datauserapi.application.query.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.datauserapi.application.query.dto.AccountStatusHistoryView;
import uk.gov.justice.laa.datauserapi.application.query.dto.UserAccountStatusView;
import uk.gov.justice.laa.datauserapi.application.query.dto.UserAccountSummaryPage;
import uk.gov.justice.laa.datauserapi.application.query.dto.UserSearchCriteria;
import uk.gov.justice.laa.datauserapi.application.query.dto.UserView;
import uk.gov.justice.laa.datauserapi.application.query.handler.GetUserAccountHandler;
import uk.gov.justice.laa.datauserapi.application.query.queryuserbyid.GetUserAccountQuery;
import uk.gov.justice.laa.datauserapi.application.query.handler.SearchUsersHandler;
import uk.gov.justice.laa.datauserapi.application.query.queryusersearch.SearchUsersQuery;
import uk.gov.justice.laa.datauserapi.application.query.service.UserAccountQueryService;
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

    public UserAccountQueryController(UserAccountQueryService queryService, SearchUsersHandler searchUsersHandler,
                                      GetUserAccountHandler getUserAccountHandler) {
        this.queryService = queryService;
        this.searchUsersHandler = searchUsersHandler;
        this.getUserAccountHandler = getUserAccountHandler;
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
            @Valid @ModelAttribute UserSearchCriteria criteria,
            @AuthenticationPrincipal Jwt jwt) {
        String actorOid = jwt.getClaimAsString("oid");
        UserAccountSummaryPage readModel = searchUsersHandler.handle(new SearchUsersQuery(criteria, actorOid));
        return ResponseEntity.ok(readModel);
    }

    @GetMapping("/users/{userEntraObjectId}")
    public ResponseEntity<UserView> getUserAccount(
            @PathVariable String userEntraObjectId,
            @AuthenticationPrincipal Jwt jwt) {
        String actorOid = jwt.getClaimAsString("oid");
        UserView readModel = getUserAccountHandler.handle(new GetUserAccountQuery(userEntraObjectId, actorOid));
        return ResponseEntity.ok(readModel);
    }
}

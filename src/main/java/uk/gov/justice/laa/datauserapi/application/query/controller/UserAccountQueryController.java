package uk.gov.justice.laa.datauserapi.application.query.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.datauserapi.application.query.dto.AccountStatusHistoryView;
import uk.gov.justice.laa.datauserapi.application.query.dto.UserAccountStatusView;
import uk.gov.justice.laa.datauserapi.application.query.service.UserAccountQueryService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/queries")
public class UserAccountQueryController {

    private final UserAccountQueryService queryService;

    public UserAccountQueryController(UserAccountQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/users/{userEntraObjectId}/status")
    public ResponseEntity<UserAccountStatusView> getUserStatus(@PathVariable UUID userEntraObjectId) {
        return ResponseEntity.ok(queryService.getUserStatus(userEntraObjectId));
    }

    @GetMapping("/audit/users/{userEntraObjectId}/history")
    public ResponseEntity<List<AccountStatusHistoryView>> getAuditHistory(@PathVariable UUID userEntraObjectId) {
        return ResponseEntity.ok(queryService.getAuditHistory(userEntraObjectId));
    }
}

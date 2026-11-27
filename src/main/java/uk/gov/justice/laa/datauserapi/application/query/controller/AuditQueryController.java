package uk.gov.justice.laa.datauserapi.application.query.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.datauserapi.application.query.handler.AuditUserDetailHandler;
import uk.gov.justice.laa.datauserapi.application.query.handler.DeletedUsersAuditHandler;
import uk.gov.justice.laa.datauserapi.application.query.queryaudit.AuditUserDetailQuery;
import uk.gov.justice.laa.datauserapi.application.query.queryaudit.DeletedUsersAuditQuery;
import uk.gov.justice.laa.datauserapi.contracts.dto.AuditDeletedUsersCriteria;
import uk.gov.justice.laa.datauserapi.contracts.response.AuditUserDetailView;
import uk.gov.justice.laa.datauserapi.contracts.response.DeletedUserAuditPage;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.security.RequiresAdminScope;

@RestController
@RequestMapping("/api/v1/queries/audit/users")
@RequiresAdminScope
public class AuditQueryController {

    private final AuditUserDetailHandler auditUserDetailHandler;
    private final DeletedUsersAuditHandler deletedUsersAuditHandler;

    public AuditQueryController(AuditUserDetailHandler auditUserDetailHandler,
                                DeletedUsersAuditHandler deletedUsersAuditHandler) {
        this.auditUserDetailHandler = auditUserDetailHandler;
        this.deletedUsersAuditHandler = deletedUsersAuditHandler;
    }

    @GetMapping("/{userEntraObjectId}")
    public ResponseEntity<AuditUserDetailView> getAuditUserDetail(
            @PathVariable String userEntraObjectId, @AuthenticationPrincipal AuthenticatedEntraUser authenticatedUser) {
        return ResponseEntity.ok(
                auditUserDetailHandler.handle(new AuditUserDetailQuery(userEntraObjectId), authenticatedUser));
    }

    @GetMapping("/deleted")
    public ResponseEntity<DeletedUserAuditPage> getDeletedUsers(
            @Valid @ModelAttribute AuditDeletedUsersCriteria criteria,
            @AuthenticationPrincipal AuthenticatedEntraUser authenticatedUser) {
        return ResponseEntity.ok(deletedUsersAuditHandler.handle(new DeletedUsersAuditQuery(criteria), authenticatedUser));
    }
}

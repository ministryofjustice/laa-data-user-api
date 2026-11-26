package uk.gov.justice.laa.datauserapi.application.query.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import uk.gov.justice.laa.datauserapi.application.query.handler.GetAppRoleByIdHandler;
import uk.gov.justice.laa.datauserapi.application.query.handler.GetRoleAssignmentRestrictionsHandler;
import uk.gov.justice.laa.datauserapi.application.query.queryappcatalogue.GetAppRoleByIdQuery;
import uk.gov.justice.laa.datauserapi.application.query.queryappcatalogue.GetRoleAssignmentRestrictionsQuery;
import uk.gov.justice.laa.datauserapi.contracts.dto.AppRoleView;
import uk.gov.justice.laa.datauserapi.contracts.response.RoleAssignmentRestrictionViewPage;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.security.RequiresReadScope;

@RestController
@RequestMapping("/api/v1/queries")
@RequiresReadScope
@Validated
public class AppRoleQueryController {

    private final GetAppRoleByIdHandler appRoleByIdHandler;
    private final GetRoleAssignmentRestrictionsHandler restrictionsHandler;

    public AppRoleQueryController(GetAppRoleByIdHandler appRoleByIdHandler,
                                  GetRoleAssignmentRestrictionsHandler restrictionsHandler) {
        this.appRoleByIdHandler = appRoleByIdHandler;
        this.restrictionsHandler = restrictionsHandler;
    }

    @GetMapping("/app-roles/{appRoleId}")
    public ResponseEntity<AppRoleView> queryAppRoleById(
            @PathVariable UUID appRoleId,
            @AuthenticationPrincipal AuthenticatedEntraUser actor) {
        return ResponseEntity.ok(appRoleByIdHandler.handle(new GetAppRoleByIdQuery(appRoleId), actor));
    }

    @GetMapping("/role-restrictions")
    public ResponseEntity<RoleAssignmentRestrictionViewPage> queryRoleAssignmentRestrictions(
            @RequestParam(name = "app_id", required = false) UUID appId,
            @RequestParam(name = "page_number", defaultValue = "0") @Min(0) int pageNumber,
            @RequestParam(name = "page_size", defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @AuthenticationPrincipal AuthenticatedEntraUser actor) {
        return ResponseEntity.ok(restrictionsHandler.handle(
                new GetRoleAssignmentRestrictionsQuery(appId, pageNumber, pageSize), actor));
    }
}

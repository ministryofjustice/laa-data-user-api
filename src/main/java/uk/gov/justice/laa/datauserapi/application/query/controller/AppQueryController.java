package uk.gov.justice.laa.datauserapi.application.query.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import uk.gov.justice.laa.datauserapi.application.query.handler.GetAppByIdHandler;
import uk.gov.justice.laa.datauserapi.application.query.handler.GetAppRolesByAppHandler;
import uk.gov.justice.laa.datauserapi.application.query.handler.GetAppsHandler;
import uk.gov.justice.laa.datauserapi.application.query.queryappcatalogue.GetAppByIdQuery;
import uk.gov.justice.laa.datauserapi.application.query.queryappcatalogue.GetAppRolesByAppQuery;
import uk.gov.justice.laa.datauserapi.application.query.queryappcatalogue.GetAppsQuery;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.contracts.dto.AppView;
import uk.gov.justice.laa.datauserapi.contracts.response.AppRoleViewList;
import uk.gov.justice.laa.datauserapi.contracts.response.AppViewList;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.security.RequiresReadScope;

@RestController
@RequestMapping("/api/v1/queries/apps")
@RequiresReadScope
public class AppQueryController {

    private final GetAppsHandler appsHandler;
    private final GetAppByIdHandler appByIdHandler;
    private final GetAppRolesByAppHandler appRolesHandler;

    public AppQueryController(GetAppsHandler appsHandler,
                              GetAppByIdHandler appByIdHandler,
                              GetAppRolesByAppHandler appRolesHandler) {
        this.appsHandler = appsHandler;
        this.appByIdHandler = appByIdHandler;
        this.appRolesHandler = appRolesHandler;
    }

    @GetMapping
    public ResponseEntity<AppViewList> queryAppsAll(@AuthenticationPrincipal AuthenticatedEntraUser actor) {
        return ResponseEntity.ok(appsHandler.handle(new GetAppsQuery(), actor));
    }

    @GetMapping("/{appId}")
    public ResponseEntity<AppView> queryAppById(
            @PathVariable UUID appId,
            @AuthenticationPrincipal AuthenticatedEntraUser actor) {
        return ResponseEntity.ok(appByIdHandler.handle(new GetAppByIdQuery(appId), actor));
    }

    @GetMapping("/{appId}/roles")
    public ResponseEntity<AppRoleViewList> queryAppRolesByApp(
            @PathVariable UUID appId,
            @RequestParam(name = "user_type", required = false) UserType userType,
            @AuthenticationPrincipal AuthenticatedEntraUser actor) {
        return ResponseEntity.ok(appRolesHandler.handle(new GetAppRolesByAppQuery(appId, userType), actor));
    }
}

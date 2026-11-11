package uk.gov.justice.laa.datauserapi.application.query.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import uk.gov.justice.laa.datauserapi.application.query.dto.OfficeViewList;
import uk.gov.justice.laa.datauserapi.application.query.handler.GetUserProfileAppRolesHandler;
import uk.gov.justice.laa.datauserapi.application.query.handler.GetUserProfileAppsHandler;
import uk.gov.justice.laa.datauserapi.application.query.handler.GetUserProfileOfficesHandler;
import uk.gov.justice.laa.datauserapi.contracts.response.AppRoleViewList;
import uk.gov.justice.laa.datauserapi.contracts.response.AppViewList;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.security.RequiresReadScope;

@RestController
@RequestMapping("/api/v1/queries/user-profiles/{userProfileId}")
@RequiresReadScope
public class UserProfileAccessQueryController {

    private final GetUserProfileAppRolesHandler appRolesHandler;
    private final GetUserProfileAppsHandler appsHandler;
    private final GetUserProfileOfficesHandler officesHandler;

    public UserProfileAccessQueryController(GetUserProfileAppRolesHandler appRolesHandler,
                                            GetUserProfileAppsHandler appsHandler,
                                            GetUserProfileOfficesHandler officesHandler) {
        this.appRolesHandler = appRolesHandler;
        this.appsHandler = appsHandler;
        this.officesHandler = officesHandler;
    }

    @GetMapping("/app-roles")
    public ResponseEntity<AppRoleViewList> queryUserProfileAppRoles(
            @PathVariable UUID userProfileId,
            @RequestParam(name = "app_id", required = false) UUID appId,
            @RequestParam(name = "appEntraClientId", required = false) UUID appEntraClientId,
            @AuthenticationPrincipal AuthenticatedEntraUser authenticatedUser) {
        return ResponseEntity.ok(appRolesHandler.handle(userProfileId, appId, appEntraClientId, authenticatedUser));
    }

    @GetMapping("/apps")
    public ResponseEntity<AppViewList> queryUserProfileApps(
            @PathVariable UUID userProfileId,
            @AuthenticationPrincipal AuthenticatedEntraUser authenticatedUser) {
        return ResponseEntity.ok(appsHandler.handle(userProfileId, authenticatedUser));
    }

    @GetMapping("/offices")
    public ResponseEntity<OfficeViewList> queryUserProfileOffices(
            @PathVariable UUID userProfileId,
            @AuthenticationPrincipal AuthenticatedEntraUser authenticatedUser) {
        return ResponseEntity.ok(officesHandler.handle(userProfileId, authenticatedUser));
    }
}

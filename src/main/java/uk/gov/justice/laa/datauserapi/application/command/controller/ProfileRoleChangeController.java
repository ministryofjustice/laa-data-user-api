package uk.gov.justice.laa.datauserapi.application.command.controller;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.HashMap;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.datauserapi.application.command.handler.ProfileRoleChangeHandler;
import uk.gov.justice.laa.datauserapi.application.command.userprofile.ProfileRoleChangeCommand;
import uk.gov.justice.laa.datauserapi.contracts.request.AssignAppRolesRequest;
import uk.gov.justice.laa.datauserapi.contracts.request.UpdateAppRolesRequest;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.contracts.response.UpdateAppRolesResponse;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.exception.InvalidRequestException;
import uk.gov.justice.laa.datauserapi.security.RequiresAdminScope;
import uk.gov.justice.laa.datauserapi.security.opa.trial.ProfileTrialOperation;
import uk.gov.justice.laa.datauserapi.security.opa.trial.ProfileTrialRequest;

@RestController
@RequestMapping("/api/v1/commands/user-profiles")
@RequiresAdminScope
public class ProfileRoleChangeController {
    private final ProfileRoleChangeHandler handler;

    public ProfileRoleChangeController(ProfileRoleChangeHandler handler) {
        this.handler = handler;
    }

    @PostMapping("/assign-app-roles")
    public CommandResult assign(@Valid @RequestBody AssignAppRolesRequest request,
                                 @AuthenticationPrincipal AuthenticatedEntraUser actor) {
        var result = handler.handle(new ProfileRoleChangeCommand(new ProfileTrialRequest(ProfileTrialOperation.ASSIGN,
                request.userProfileId(), request.appRoleIds(), Map.of(), actor), null), actor);
        return CommandResult.success(result.message());
    }

    @PostMapping("/update-app-roles")
    public UpdateAppRolesResponse update(@Valid @RequestBody UpdateAppRolesRequest request,
                                          @AuthenticationPrincipal AuthenticatedEntraUser actor) {
        Map<UUID, Set<UUID>> apps = new HashMap<>();
        for (var entry : request.appsAndRoles()) {
            if (apps.putIfAbsent(entry.appId(), entry.appRoleIds()) != null) {
                throw new InvalidRequestException(InvalidRequestException.Reason.INVALID_ROLE_UPDATE,
                        "A role update must not repeat applications");
            }
        }
        return handler.handle(new ProfileRoleChangeCommand(new ProfileTrialRequest(ProfileTrialOperation.UPDATE,
                request.userProfileId(), Set.of(), apps, actor), null), actor);
    }
}

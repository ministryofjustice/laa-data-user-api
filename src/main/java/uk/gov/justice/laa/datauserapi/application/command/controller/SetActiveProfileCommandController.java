package uk.gov.justice.laa.datauserapi.application.command.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.datauserapi.application.command.handler.SetActiveProfileHandler;
import uk.gov.justice.laa.datauserapi.application.command.userprofile.SetActiveProfileCommand;
import uk.gov.justice.laa.datauserapi.contracts.request.SetActiveProfileRequest;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.security.RequiresAdminScope;

@RestController
@RequestMapping("/api/v1/commands/users")
@RequiresAdminScope
public class SetActiveProfileCommandController {

    private final SetActiveProfileHandler handler;

    public SetActiveProfileCommandController(SetActiveProfileHandler handler) {
        this.handler = handler;
    }

    @PostMapping("/set-active-profile")
    public ResponseEntity<CommandResult> setActiveProfile(
            @Valid @RequestBody SetActiveProfileRequest request,
            @AuthenticationPrincipal AuthenticatedEntraUser authenticatedUser) {
        SetActiveProfileCommand command = SetActiveProfileCommand.builder()
                .userEntraObjectId(request.userEntraObjectId())
                .userProfileId(request.userProfileId())
                .build();
        return ResponseEntity.ok(handler.handle(command, authenticatedUser));
    }
}

package uk.gov.justice.laa.datauserapi.application.command.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.datauserapi.application.command.handler.RefreshSilasStatusHandler;
import uk.gov.justice.laa.datauserapi.application.command.userprofile.RefreshSilasStatusCommand;
import uk.gov.justice.laa.datauserapi.application.command.handler.UpdateUserProfileOfficesHandler;
import uk.gov.justice.laa.datauserapi.application.command.handler.UpdateUserProfileUnrestrictedOfficesHandler;
import uk.gov.justice.laa.datauserapi.application.command.mapper.UpdateUserProfileOfficesMapper;
import uk.gov.justice.laa.datauserapi.application.command.userprofile.UpdateUserProfileOfficesCommand;
import uk.gov.justice.laa.datauserapi.application.command.userprofile.UpdateUserProfileUnrestrictedOfficesCommand;
import uk.gov.justice.laa.datauserapi.contracts.request.UpdateOfficesRequest;
import uk.gov.justice.laa.datauserapi.contracts.request.UpdateOfficesUnrestrictedCommand;
import uk.gov.justice.laa.datauserapi.contracts.request.UpdateUserProfileOfficesRequest;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.security.RequiresAdminScope;
import uk.gov.justice.laa.datauserapi.security.RequiresSystemAdminScope;

@Slf4j
@RestController
@RequestMapping("/api/v1/commands/user-profiles")
@RequiresAdminScope
@RequiresSystemAdminScope
public class UserProfileCommandController {

    private final UpdateUserProfileOfficesHandler updateUserProfileOfficesHandler;
    private final RefreshSilasStatusHandler refreshSilasStatusHandler;
    private final UpdateUserProfileOfficesMapper updateUserProfileOfficesMapper;
    private final UpdateUserProfileUnrestrictedOfficesHandler updateUserProfileUnrestrictedOfficesHandler;

    public UserProfileCommandController(
            UpdateUserProfileOfficesHandler updateUserProfileOfficesHandler,
            RefreshSilasStatusHandler refreshSilasStatusHandler,
            UpdateUserProfileOfficesMapper updateUserProfileOfficesMapper,
            UpdateUserProfileUnrestrictedOfficesHandler updateUserProfileUnrestrictedOfficesHandler) {
        this.updateUserProfileOfficesHandler = updateUserProfileOfficesHandler;
        this.refreshSilasStatusHandler = refreshSilasStatusHandler;
        this.updateUserProfileOfficesMapper = updateUserProfileOfficesMapper;
        this.updateUserProfileUnrestrictedOfficesHandler = updateUserProfileUnrestrictedOfficesHandler;
    }

    @PostMapping("/update-offices")
    public ResponseEntity<CommandResult> updateOffices(
            @Valid @RequestBody UpdateOfficesRequest request,
            @AuthenticationPrincipal AuthenticatedEntraUser authenticatedUser) {
        if (request instanceof UpdateUserProfileOfficesRequest officesRequest) {
            log.info("Processing office update for user profile {} by {}",
                    officesRequest.userProfileId(), authenticatedUser.entraOid());
            UpdateUserProfileOfficesCommand command = updateUserProfileOfficesMapper.toUpdateUserProfileOfficesCommand(officesRequest);
            CommandResult result = updateUserProfileOfficesHandler.handle(command, authenticatedUser.entraOid());
            return ResponseEntity.ok(result);
        }

        UpdateOfficesUnrestrictedCommand unrestrictedRequest = (UpdateOfficesUnrestrictedCommand) request;
        log.info("Processing unrestricted office access update for user profile {} by {}",
                unrestrictedRequest.userProfileId(), authenticatedUser.entraOid());
        UpdateUserProfileUnrestrictedOfficesCommand command = updateUserProfileOfficesMapper.toUpdateUserProfileUnrestrictedOfficesCommand(unrestrictedRequest);
        CommandResult result = updateUserProfileUnrestrictedOfficesHandler.handle(command, authenticatedUser.entraOid());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/refresh-silas-status")
    public ResponseEntity<CommandResult> refreshSilasStatus(
            @AuthenticationPrincipal AuthenticatedEntraUser authenticatedUser) {
        log.info("Processing SiLAS status refresh requested by {}", authenticatedUser.entraOid());
        return ResponseEntity.ok(refreshSilasStatusHandler.handle(new RefreshSilasStatusCommand(), authenticatedUser));
    }
}

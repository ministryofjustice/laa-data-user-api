package uk.gov.justice.laa.datauserapi.application.command.controller;

import static net.logstash.logback.argument.StructuredArguments.kv;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.datauserapi.application.command.handler.UpdateUserProfileOfficesHandler;
import uk.gov.justice.laa.datauserapi.application.command.handler.UpdateUserProfileUnrestrictedOfficesHandler;
import uk.gov.justice.laa.datauserapi.application.command.mapper.UpdateUserProfileOfficesMapper;
import uk.gov.justice.laa.datauserapi.application.command.userprofile.UpdateUserProfileOfficesCommand;
import uk.gov.justice.laa.datauserapi.application.command.userprofile.UpdateUserProfileUnrestrictedOfficesCommand;
import uk.gov.justice.laa.datauserapi.contracts.request.UpdateOfficesRequest;
import uk.gov.justice.laa.datauserapi.contracts.request.UpdateUserProfileOfficesRequest;
import uk.gov.justice.laa.datauserapi.contracts.request.UpdateUserProfileOfficesUnrestrictedRequest;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.security.RequiresAdminScope;

@Slf4j
@RestController
@RequestMapping("/api/v1/commands/user-profiles")
@RequiresAdminScope
public class UserProfileCommandController {

    private final UpdateUserProfileOfficesHandler updateUserProfileOfficesHandler;
    private final UpdateUserProfileOfficesMapper updateUserProfileOfficesMapper;
    private final UpdateUserProfileUnrestrictedOfficesHandler updateUserProfileUnrestrictedOfficesHandler;

    public UserProfileCommandController(
            UpdateUserProfileOfficesHandler updateUserProfileOfficesHandler,
            UpdateUserProfileOfficesMapper updateUserProfileOfficesMapper,
            UpdateUserProfileUnrestrictedOfficesHandler updateUserProfileUnrestrictedOfficesHandler) {
        this.updateUserProfileOfficesHandler = updateUserProfileOfficesHandler;
        this.updateUserProfileOfficesMapper = updateUserProfileOfficesMapper;
        this.updateUserProfileUnrestrictedOfficesHandler = updateUserProfileUnrestrictedOfficesHandler;
    }

    @PostMapping("/update-offices")
    public ResponseEntity<CommandResult> updateOffices(
            @Valid @RequestBody UpdateOfficesRequest request,
            @AuthenticationPrincipal AuthenticatedEntraUser authenticatedUser) {
        if (request instanceof UpdateUserProfileOfficesRequest officesRequest) {
            log.info("Processing office update",
                    kv("userProfileId", officesRequest.userProfileId()),
                    kv("actorEntraOid", authenticatedUser.entraOid()));
            UpdateUserProfileOfficesCommand command = updateUserProfileOfficesMapper.toUpdateUserProfileOfficesCommand(officesRequest);
            CommandResult result = updateUserProfileOfficesHandler.handle(command, authenticatedUser);
            return ResponseEntity.ok(result);
        }

        UpdateUserProfileOfficesUnrestrictedRequest unrestrictedRequest = (UpdateUserProfileOfficesUnrestrictedRequest) request;
        log.info("Processing unrestricted office access update",
                kv("userProfileId", unrestrictedRequest.userProfileId()),
                kv("actorEntraOid", authenticatedUser.entraOid()));
        UpdateUserProfileUnrestrictedOfficesCommand command = updateUserProfileOfficesMapper.toUpdateUserProfileUnrestrictedOfficesCommand(unrestrictedRequest);
        CommandResult result = updateUserProfileUnrestrictedOfficesHandler.handle(command, authenticatedUser);
        return ResponseEntity.ok(result);
    }

}

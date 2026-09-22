package uk.gov.justice.laa.datauserapi.application.command.controller;


import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.datauserapi.application.command.handler.DisableUserHandler;
import uk.gov.justice.laa.datauserapi.application.command.handler.EnableUserHandler;
import uk.gov.justice.laa.datauserapi.application.command.mapper.DisableUserMapper;
import uk.gov.justice.laa.datauserapi.application.command.mapper.EnableUserMapper;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.DisableUserCommand;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.EnableUserCommand;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.contracts.request.DisableUserRequest;
import uk.gov.justice.laa.datauserapi.contracts.request.EnableUserRequest;

@Slf4j
@RestController
@RequestMapping("/api/v1/commands")
public class UserAccountCommandController {

    private final EnableUserHandler enableUserHandler;
    private final EnableUserMapper enableUserMapper;
    private final DisableUserHandler disableUserHandler;
    private final DisableUserMapper disableUserMapper;

    public UserAccountCommandController(EnableUserHandler enableUserHandler, EnableUserMapper enableUserMapper, DisableUserHandler disableUserHandler, DisableUserMapper disableUserMapper) {
        this.enableUserHandler = enableUserHandler;
        this.enableUserMapper = enableUserMapper;
        this.disableUserHandler = disableUserHandler;
        this.disableUserMapper = disableUserMapper;
    }

    @PostMapping("/users/enable")
    public ResponseEntity<CommandResult> enableUser(
            @Valid @RequestBody EnableUserRequest enableUserRequest,
            @AuthenticationPrincipal Jwt jwt) {
        log.info("Processing enable user command for user: {}", enableUserRequest.userEntraObjectId());
        String actorId = resolveActor(jwt);
        EnableUserCommand enableUserCommand = enableUserMapper.toEnableUserCommand(enableUserRequest);
        CommandResult result = enableUserHandler.handle(enableUserCommand, actorId);
        log.info("User account enabled with ID: {} by {}", enableUserRequest.userEntraObjectId(), actorId);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/users/disable")
    public ResponseEntity<CommandResult> disableUser(
            @Valid @RequestBody DisableUserRequest disableUserRequest,
            @AuthenticationPrincipal Jwt jwt) {
        log.info("Processing disable user command for user: {}", disableUserRequest.userEntraObjectId());
        String actorId = resolveActor(jwt);
        DisableUserCommand disableUserCommand = disableUserMapper.toDisableUserCommand(disableUserRequest);
        CommandResult result = disableUserHandler.handle(disableUserCommand, actorId);
        log.info("User account disabled with ID: {} by {} with reason: {}", disableUserRequest.userEntraObjectId(),
                actorId, disableUserRequest.disableReason());
        return ResponseEntity.ok(result);
    }

    private String resolveActor(Jwt jwt) {
        return (jwt != null && jwt.getSubject() != null) ? jwt.getSubject() : "system";
    }
}

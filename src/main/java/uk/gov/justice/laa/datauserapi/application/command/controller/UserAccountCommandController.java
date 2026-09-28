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
import uk.gov.justice.laa.datauserapi.application.command.handler.DeactivateUserHandler;
import uk.gov.justice.laa.datauserapi.application.command.handler.ReactivateUserHandler;
import uk.gov.justice.laa.datauserapi.application.command.mapper.DeactivateUserMapper;
import uk.gov.justice.laa.datauserapi.application.command.mapper.ReactivateUserMapper;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.DeactivateUserCommand;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.ReactivateUserCommand;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.contracts.request.DeactivateUserRequest;
import uk.gov.justice.laa.datauserapi.contracts.request.ReactivateUserRequest;

@Slf4j
@RestController
@RequestMapping("/api/v1/commands")
public class UserAccountCommandController {

    private final ReactivateUserHandler reactivateUserHandler;
    private final ReactivateUserMapper reactivateUserMapper;
    private final DeactivateUserHandler deactivateUserHandler;
    private final DeactivateUserMapper deactivateUserMapper;

    public UserAccountCommandController(ReactivateUserHandler reactivateUserHandler, ReactivateUserMapper reactivateUserMapper, DeactivateUserHandler deactivateUserHandler, DeactivateUserMapper deactivateUserMapper) {
        this.reactivateUserHandler = reactivateUserHandler;
        this.reactivateUserMapper = reactivateUserMapper;
        this.deactivateUserHandler = deactivateUserHandler;
        this.deactivateUserMapper = deactivateUserMapper;
    }

    @PostMapping("/users/reactivate")
    public ResponseEntity<CommandResult> reactivateUser(
            @Valid @RequestBody ReactivateUserRequest reactivateUserRequest,
            @AuthenticationPrincipal Jwt jwt) {
        log.info("Processing reactivate user command for user: {}", reactivateUserRequest.userEntraObjectId());
        String actorId = resolveActor(jwt);
        ReactivateUserCommand reactivateUserCommand = reactivateUserMapper.toReactivateUserCommand(reactivateUserRequest);
        CommandResult result = reactivateUserHandler.handle(reactivateUserCommand, actorId);
        log.info("User account reactivated with ID: {} by {}", reactivateUserRequest.userEntraObjectId(), actorId);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/users/deactivate")
    public ResponseEntity<CommandResult> deactivateUser(
            @Valid @RequestBody DeactivateUserRequest deactivateUserRequest,
            @AuthenticationPrincipal Jwt jwt) {
        log.info("Processing deactivate user command for user: {}", deactivateUserRequest.userEntraObjectId());
        String actorId = resolveActor(jwt);
        DeactivateUserCommand deactivateUserCommand = deactivateUserMapper.toDeactivateUserCommand(deactivateUserRequest);
        CommandResult result = deactivateUserHandler.handle(deactivateUserCommand, actorId);
        log.info("User account deactivated with ID: {} by {} with reason: {}", deactivateUserRequest.userEntraObjectId(),
                actorId, deactivateUserRequest.deactivateReason());
        return ResponseEntity.ok(result);
    }

    private String resolveActor(Jwt jwt) {
        return (jwt != null && jwt.getSubject() != null) ? jwt.getSubject() : "system";
    }
}

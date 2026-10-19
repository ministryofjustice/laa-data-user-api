package uk.gov.justice.laa.datauserapi.application.command.controller;


import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.datauserapi.application.command.handler.DeactivateUserHandler;
import uk.gov.justice.laa.datauserapi.application.command.handler.ReactivateUserHandler;
import uk.gov.justice.laa.datauserapi.application.command.mapper.DeactivateUserMapper;
import uk.gov.justice.laa.datauserapi.application.command.handler.UpdateUserDetailsHandler;
import uk.gov.justice.laa.datauserapi.application.command.mapper.ReactivateUserMapper;
import uk.gov.justice.laa.datauserapi.application.command.mapper.UpdateUserDetailsMapper;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.DeactivateUserCommand;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.ReactivateUserCommand;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.UpdateUserDetailsCommand;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.contracts.request.DeactivateUserRequest;
import uk.gov.justice.laa.datauserapi.contracts.request.ReactivateUserRequest;
import uk.gov.justice.laa.datauserapi.contracts.request.UpdateUserDetailsRequest;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.security.RequiresAdminScope;

@Slf4j
@RestController
@RequestMapping("/api/v1/commands")
@RequiresAdminScope
public class UserAccountCommandController implements CommandControllerSupport {

    private final ReactivateUserHandler reactivateUserHandler;
    private final ReactivateUserMapper reactivateUserMapper;
    private final DeactivateUserHandler deactivateUserHandler;
    private final DeactivateUserMapper deactivateUserMapper;
    private final UpdateUserDetailsHandler updateUserDetailsHandler;
    private final UpdateUserDetailsMapper updateUserDetailsMapper;

    public UserAccountCommandController(ReactivateUserHandler reactivateUserHandler, ReactivateUserMapper reactivateUserMapper,
                                        DeactivateUserHandler deactivateUserHandler, DeactivateUserMapper deactivateUserMapper,
                                        UpdateUserDetailsHandler updateUserDetailsHandler, UpdateUserDetailsMapper updateUserDetailsMapper) {
        this.reactivateUserHandler = reactivateUserHandler;
        this.reactivateUserMapper = reactivateUserMapper;
        this.deactivateUserHandler = deactivateUserHandler;
        this.deactivateUserMapper = deactivateUserMapper;
        this.updateUserDetailsHandler = updateUserDetailsHandler;
        this.updateUserDetailsMapper = updateUserDetailsMapper;
    }

    @PostMapping("/users/reactivate")
    public ResponseEntity<CommandResult> reactivateUser(
            @Valid @RequestBody ReactivateUserRequest reactivateUserRequest,
            @AuthenticationPrincipal AuthenticatedEntraUser authenticatedUser) {
        log.info("Processing reactivate user command for user: {}", reactivateUserRequest.userEntraObjectId());
        ReactivateUserCommand reactivateUserCommand = reactivateUserMapper.toReactivateUserCommand(reactivateUserRequest);
        CommandResult result = reactivateUserHandler.handle(reactivateUserCommand, authenticatedUser);
        log.info("User account reactivated with ID: {} by {}", reactivateUserRequest.userEntraObjectId(), authenticatedUser.entraOid());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/users/deactivate")
    public ResponseEntity<CommandResult> deactivateUser(
            @Valid @RequestBody DeactivateUserRequest deactivateUserRequest,
            @AuthenticationPrincipal AuthenticatedEntraUser authenticatedUser) {
        log.info("Processing deactivate user command for user: {}", deactivateUserRequest.userEntraObjectId());
        DeactivateUserCommand deactivateUserCommand = deactivateUserMapper.toDeactivateUserCommand(deactivateUserRequest);
        CommandResult result = deactivateUserHandler.handle(deactivateUserCommand, authenticatedUser);
        log.info("User account deactivated with ID: {} by {} with reason: {}", deactivateUserRequest.userEntraObjectId(),
                authenticatedUser.entraOid(), deactivateUserRequest.deactivateReason());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/users/update-details")
    public ResponseEntity<CommandResult> updateUserDetails(
            @Valid @RequestBody UpdateUserDetailsRequest updateUserDetailsRequest,
            @AuthenticationPrincipal AuthenticatedEntraUser authenticatedUser) {
        log.info("Processing update user details command for user: {}", updateUserDetailsRequest.userEntraObjectId());
        UpdateUserDetailsCommand command = updateUserDetailsMapper.toUpdateUserDetailsCommand(updateUserDetailsRequest);
        CommandResult result = updateUserDetailsHandler.handle(command, authenticatedUser);
        return ResponseEntity.ok(result);
    }

}

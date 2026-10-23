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
import uk.gov.justice.laa.datauserapi.application.command.handler.DeleteUserHandler;
import uk.gov.justice.laa.datauserapi.application.command.handler.ReactivateUserHandler;
import uk.gov.justice.laa.datauserapi.application.command.mapper.DeactivateUserMapper;
import uk.gov.justice.laa.datauserapi.application.command.mapper.DeleteUserMapper;
import uk.gov.justice.laa.datauserapi.application.command.mapper.ReactivateUserMapper;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.DeactivateUserCommand;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.DeleteUserCommand;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.ReactivateUserCommand;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.contracts.request.DeactivateUserRequest;
import uk.gov.justice.laa.datauserapi.contracts.request.DeleteUserRequest;
import uk.gov.justice.laa.datauserapi.contracts.request.ReactivateUserRequest;
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
    private final DeleteUserHandler deleteUserHandler;
    private final DeleteUserMapper deleteUserMapper;

    public UserAccountCommandController(ReactivateUserHandler reactivateUserHandler, ReactivateUserMapper reactivateUserMapper,
                                        DeactivateUserHandler deactivateUserHandler, DeactivateUserMapper deactivateUserMapper,
                                        DeleteUserHandler deleteUserHandler, DeleteUserMapper deleteUserMapper) {
        this.reactivateUserHandler = reactivateUserHandler;
        this.reactivateUserMapper = reactivateUserMapper;
        this.deactivateUserHandler = deactivateUserHandler;
        this.deactivateUserMapper = deactivateUserMapper;
        this.deleteUserHandler = deleteUserHandler;
        this.deleteUserMapper = deleteUserMapper;
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

    @PostMapping("/users/delete")
    public ResponseEntity<CommandResult> deleteUser(
            @Valid @RequestBody DeleteUserRequest deleteUserRequest,
            @AuthenticationPrincipal AuthenticatedEntraUser authenticatedUser) {
        log.info("Processing delete user command for user: {}", deleteUserRequest.userEntraObjectId());
        DeleteUserCommand deleteUserCommand = deleteUserMapper.toDeleteUserCommand(deleteUserRequest);
        CommandResult result = deleteUserHandler.handle(deleteUserCommand, authenticatedUser);
        log.info("Delete user command for ID: {} by {} with reason: {} completed, success: {}",
                deleteUserRequest.userEntraObjectId(), authenticatedUser.entraOid(), deleteUserRequest.deleteReason(), result.success());
        return ResponseEntity.ok(result);
    }

}

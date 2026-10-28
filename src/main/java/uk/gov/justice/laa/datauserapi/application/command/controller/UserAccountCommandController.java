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
import uk.gov.justice.laa.datauserapi.application.command.handler.DeactivateUserHandler;
import uk.gov.justice.laa.datauserapi.application.command.handler.DeleteUserHandler;
import uk.gov.justice.laa.datauserapi.application.command.handler.ReactivateUserHandler;
import uk.gov.justice.laa.datauserapi.application.command.controller.mapper.ConvertToMultiFirmCommandMapper;
import uk.gov.justice.laa.datauserapi.application.command.mapper.DeactivateUserMapper;
import uk.gov.justice.laa.datauserapi.application.command.mapper.DeleteUserMapper;
import uk.gov.justice.laa.datauserapi.application.command.handler.UpdateUserDetailsHandler;
import uk.gov.justice.laa.datauserapi.application.command.mapper.ReactivateUserMapper;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.convertmultifirm.ConvertToMultiFirmCommand;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.convertmultifirm.ConvertToMultiFirmHandler;
import uk.gov.justice.laa.datauserapi.application.command.mapper.UpdateUserDetailsMapper;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.DeactivateUserCommand;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.DeleteUserCommand;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.ReactivateUserCommand;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.UpdateUserDetailsCommand;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.contracts.request.ConvertToMultiFirmRequest;
import uk.gov.justice.laa.datauserapi.contracts.request.DeactivateUserRequest;
import uk.gov.justice.laa.datauserapi.contracts.request.DeleteUserRequest;
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
    private final DeleteUserHandler deleteUserHandler;
    private final DeleteUserMapper deleteUserMapper;
    private final ConvertToMultiFirmHandler convertToMultiFirmHandler;
    private final ConvertToMultiFirmCommandMapper convertToMultiFirmCommandMapper;

    public UserAccountCommandController(ReactivateUserHandler reactivateUserHandler, ReactivateUserMapper reactivateUserMapper,
                                        DeactivateUserHandler deactivateUserHandler, DeactivateUserMapper deactivateUserMapper,
                                        UpdateUserDetailsHandler updateUserDetailsHandler, UpdateUserDetailsMapper updateUserDetailsMapper,
                                        DeleteUserHandler deleteUserHandler, DeleteUserMapper deleteUserMapper,
                                        ConvertToMultiFirmHandler convertToMultiFirmHandler,
                                        ConvertToMultiFirmCommandMapper convertToMultiFirmCommandMapper) {
        this.reactivateUserHandler = reactivateUserHandler;
        this.reactivateUserMapper = reactivateUserMapper;
        this.deactivateUserHandler = deactivateUserHandler;
        this.deactivateUserMapper = deactivateUserMapper;
        this.updateUserDetailsHandler = updateUserDetailsHandler;
        this.updateUserDetailsMapper = updateUserDetailsMapper;
        this.deleteUserHandler = deleteUserHandler;
        this.deleteUserMapper = deleteUserMapper;
        this.convertToMultiFirmHandler = convertToMultiFirmHandler;
        this.convertToMultiFirmCommandMapper = convertToMultiFirmCommandMapper;
    }

    @PostMapping("/users/reactivate")
    public ResponseEntity<CommandResult> reactivateUser(
            @Valid @RequestBody ReactivateUserRequest reactivateUserRequest,
            @AuthenticationPrincipal AuthenticatedEntraUser authenticatedUser) {
        log.info("Processing reactivate user command",
                kv("entraOid", reactivateUserRequest.userEntraObjectId()));
        ReactivateUserCommand reactivateUserCommand = reactivateUserMapper.toReactivateUserCommand(reactivateUserRequest);
        CommandResult result = reactivateUserHandler.handle(reactivateUserCommand, authenticatedUser);
        log.info("User account reactivated",
                kv("entraOid", reactivateUserRequest.userEntraObjectId()),
                kv("actorEntraOid", authenticatedUser.entraOid()));
        return ResponseEntity.ok(result);
    }

    @PostMapping("/users/deactivate")
    public ResponseEntity<CommandResult> deactivateUser(
            @Valid @RequestBody DeactivateUserRequest deactivateUserRequest,
            @AuthenticationPrincipal AuthenticatedEntraUser authenticatedUser) {
        log.info("Processing deactivate user command",
                kv("entraOid", deactivateUserRequest.userEntraObjectId()));
        DeactivateUserCommand deactivateUserCommand = deactivateUserMapper.toDeactivateUserCommand(deactivateUserRequest);
        CommandResult result = deactivateUserHandler.handle(deactivateUserCommand, authenticatedUser);
        log.info("User account deactivated",
                kv("entraOid", deactivateUserRequest.userEntraObjectId()),
                kv("actorEntraOid", authenticatedUser.entraOid()),
                kv("reason", deactivateUserRequest.deactivateReason()));
        return ResponseEntity.ok(result);
    }

    @PostMapping("/users/update-details")
    public ResponseEntity<CommandResult> updateUserDetails(
            @Valid @RequestBody UpdateUserDetailsRequest updateUserDetailsRequest,
            @AuthenticationPrincipal AuthenticatedEntraUser authenticatedUser) {
        log.info("Processing update user details command",
                kv("entraOid", updateUserDetailsRequest.userEntraObjectId()));
        UpdateUserDetailsCommand command = updateUserDetailsMapper.toUpdateUserDetailsCommand(updateUserDetailsRequest);
        CommandResult result = updateUserDetailsHandler.handle(command, authenticatedUser);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/users/delete")
    public ResponseEntity<CommandResult> deleteUser(
            @Valid @RequestBody DeleteUserRequest deleteUserRequest,
            @AuthenticationPrincipal AuthenticatedEntraUser authenticatedUser) {
        log.info("Processing delete user command",
                kv("entraOid", deleteUserRequest.userEntraObjectId()));
        DeleteUserCommand deleteUserCommand = deleteUserMapper.toDeleteUserCommand(deleteUserRequest);
        CommandResult result = deleteUserHandler.handle(deleteUserCommand, authenticatedUser);
        log.info("Delete user command completed",
                kv("entraOid", deleteUserRequest.userEntraObjectId()),
                kv("actorEntraOid", authenticatedUser.entraOid()),
                kv("reason", deleteUserRequest.deleteReason()),
                kv("success", result.success()));
        return ResponseEntity.ok(result);
    }

    @PostMapping("/users/convert-to-multi-firm")
    public ResponseEntity<CommandResult> convertToMultiFirm(
            @Valid @RequestBody ConvertToMultiFirmRequest request,
            @AuthenticationPrincipal AuthenticatedEntraUser authenticatedUser) {
        log.info("Processing convert to multi-firm command for user: {}", request.userEntraObjectId());
        ConvertToMultiFirmCommand command = convertToMultiFirmCommandMapper.toCommand(request);
        CommandResult result = convertToMultiFirmHandler.handle(command, authenticatedUser);
        log.info("User {} converted to multi-firm by {}", request.userEntraObjectId(), authenticatedUser.entraOid());
        return ResponseEntity.ok(result);
    }

}

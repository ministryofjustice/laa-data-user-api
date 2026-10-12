package uk.gov.justice.laa.datauserapi.application.command.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.datauserapi.application.command.handler.BulkDeactivateFirmUsersHandler;
import uk.gov.justice.laa.datauserapi.application.command.mapper.BulkDeactivateFirmUsersMapper;
import uk.gov.justice.laa.datauserapi.contracts.request.BulkDeactivateFirmUsersRequest;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/commands/firms")
public class FirmCommandController {

    private final BulkDeactivateFirmUsersHandler bulkDeactivateFirmUsersHandler;
    private final BulkDeactivateFirmUsersMapper bulkDeactivateFirmUsersMapper;

    public FirmCommandController(
            BulkDeactivateFirmUsersHandler bulkDeactivateFirmUsersHandler,
            BulkDeactivateFirmUsersMapper bulkDeactivateFirmUsersMapper) {
        this.bulkDeactivateFirmUsersHandler = bulkDeactivateFirmUsersHandler;
        this.bulkDeactivateFirmUsersMapper = bulkDeactivateFirmUsersMapper;
    }

    @PostMapping("/bulk-deactivate-users")
    public ResponseEntity<CommandResult> bulkDeactivateUsers(
            @Valid @RequestBody BulkDeactivateFirmUsersRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        String actorId = resolveActorId(jwt);
        log.info("Processing bulk user deactivation for firm {} by {}", request.firmId(), actorId);
        CommandResult result = bulkDeactivateFirmUsersHandler.handle(
                bulkDeactivateFirmUsersMapper.toCommand(request), actorId);
        return ResponseEntity.ok(result);
    }

    private String resolveActorId(Jwt jwt) {
        if (jwt == null || jwt.getClaimAsString("oid") == null) {
            throw new AccessDeniedException("Authenticated user object ID is required");
        }
        try {
            return UUID.fromString(jwt.getClaimAsString("oid")).toString();
        } catch (IllegalArgumentException e) {
            throw new AccessDeniedException("Authenticated user object ID must be a UUID", e);
        }
    }
}

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
import uk.gov.justice.laa.datauserapi.application.command.handler.BulkDeactivateFirmUsersHandler;
import uk.gov.justice.laa.datauserapi.application.command.mapper.BulkDeactivateFirmUsersMapper;
import uk.gov.justice.laa.datauserapi.contracts.request.BulkDeactivateFirmUsersRequest;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.security.RequiresAdminScope;

@Slf4j
@RestController
@RequiresAdminScope
@RequestMapping("/api/v1/commands/firms")
public class FirmCommandController implements CommandControllerSupport {

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
        String actorOid = resolveActor(jwt);
        log.info("Processing bulk user deactivation for firm {} by {}", request.firmId(), actorOid);
        CommandResult result = bulkDeactivateFirmUsersHandler.handle(
                bulkDeactivateFirmUsersMapper.toCommand(request), actorOid);
        return ResponseEntity.ok(result);
    }

}

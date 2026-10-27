package uk.gov.justice.laa.datauserapi.application.command.controller;

import static net.logstash.logback.argument.StructuredArguments.kv;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.datauserapi.application.command.ccms.TriggerCcmsRoleSyncCommand;
import uk.gov.justice.laa.datauserapi.application.command.handler.TriggerCcmsRoleSyncHandler;
import uk.gov.justice.laa.datauserapi.contracts.response.TriggerCcmsRoleSyncResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.security.RequiresSystemAdminScope;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/commands/ccms")
@RequiresSystemAdminScope
public class CcmsCommandController {

    private final TriggerCcmsRoleSyncHandler handler;

    @PostMapping("/trigger-role-sync")
    public ResponseEntity<TriggerCcmsRoleSyncResult> triggerRoleSync(
            @AuthenticationPrincipal AuthenticatedEntraUser authenticatedUser) {
        log.info("Processing CCMS role sync request",
                kv("actorEntraOid", authenticatedUser.entraOid()));
        return ResponseEntity.ok(handler.handle(new TriggerCcmsRoleSyncCommand(), authenticatedUser));
    }
}

package uk.gov.justice.laa.datauserapi.application.command.controller;

import static net.logstash.logback.argument.StructuredArguments.kv;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.datauserapi.application.command.handler.RefreshSilasStatusHandler;
import uk.gov.justice.laa.datauserapi.application.command.userprofile.RefreshSilasStatusCommand;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.security.RequiresSystemAdminScope;

@Slf4j
@RestController
@RequestMapping("/api/v1/commands/user-profiles")
@RequiresSystemAdminScope
public class UserProfileSystemAdminCommandController {

    private final RefreshSilasStatusHandler refreshSilasStatusHandler;

    public UserProfileSystemAdminCommandController(
            RefreshSilasStatusHandler refreshSilasStatusHandler) {
        this.refreshSilasStatusHandler = refreshSilasStatusHandler;
    }

    @RequiresSystemAdminScope
    @PostMapping("/refresh-silas-status")
    public ResponseEntity<CommandResult> refreshSilasStatus(
            @AuthenticationPrincipal AuthenticatedEntraUser authenticatedUser) {
        log.info("Processing SiLAS status refresh request",
                kv("actorEntraOid", authenticatedUser.entraOid()));
        return ResponseEntity.ok(refreshSilasStatusHandler.handle(new RefreshSilasStatusCommand(), authenticatedUser));
    }
}

package uk.gov.justice.laa.datauserapi.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.datauserapi.contracts.request.CreateUserAccountCommand;
import uk.gov.justice.laa.datauserapi.contracts.response.CreateUserAccountResult;
import uk.gov.justice.laa.datauserapi.application.command.service.UserAccountCreationService;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.security.RequiresAdminScope;

@RestController
@RequestMapping("/api/v1/commands/users")
@RequiresAdminScope
public class CreateUserAccountController {
    private final UserAccountCreationService service;

    public CreateUserAccountController(UserAccountCreationService service) {
        this.service = service;
    }

    @PostMapping("/create")
    public ResponseEntity<CreateUserAccountResult> create(
            @Valid @RequestBody CreateUserAccountCommand command,
            @AuthenticationPrincipal AuthenticatedEntraUser authenticatedUser) {
        String actorId = authenticatedUser.entraOid();
        return ResponseEntity.status(201).body(service.create(command, actorId));
    }
}

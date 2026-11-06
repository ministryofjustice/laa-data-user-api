package uk.gov.justice.laa.datauserapi.application.command.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.datauserapi.application.command.handler.CreateUserAccountHandler;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.CreateUserAccountCommand;
import uk.gov.justice.laa.datauserapi.contracts.request.CreateUserAccountRequest;
import uk.gov.justice.laa.datauserapi.contracts.response.CreateUserAccountResponse;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.security.RequiresAdminScope;

@RestController
@RequestMapping("/api/v1/commands/users")
@RequiresAdminScope
public class CreateUserAccountController {
    private final CreateUserAccountHandler handler;

    public CreateUserAccountController(CreateUserAccountHandler handler) {
        this.handler = handler;
    }

    @PostMapping("/create")
    public ResponseEntity<CreateUserAccountResponse> create(
            @Valid @RequestBody CreateUserAccountRequest request,
            @AuthenticationPrincipal AuthenticatedEntraUser authenticatedUser) {
        CreateUserAccountCommand command = new CreateUserAccountCommand(request.email(), request.firstName(),
                request.lastName(), request.userType(), request.multiFirmUser(), request.firmId());
        return ResponseEntity.status(201).body(handler.handle(command, authenticatedUser));
    }
}

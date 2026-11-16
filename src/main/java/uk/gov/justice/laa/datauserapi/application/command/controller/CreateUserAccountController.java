package uk.gov.justice.laa.datauserapi.application.command.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.datauserapi.application.command.handler.CreateUserAccountHandler;
import uk.gov.justice.laa.datauserapi.application.command.mapper.UserAccountCreationMapper;
import uk.gov.justice.laa.datauserapi.contracts.request.CreateUserAccountRequest;
import uk.gov.justice.laa.datauserapi.contracts.response.CreateUserAccountResponse;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.security.RequiresAdminScope;

@RestController
@RequestMapping("/api/v1/commands/users")
@RequiresAdminScope
public class CreateUserAccountController {
    private final CreateUserAccountHandler handler;
    private final UserAccountCreationMapper mapper;

    public CreateUserAccountController(CreateUserAccountHandler handler, UserAccountCreationMapper mapper) {
        this.handler = handler;
        this.mapper = mapper;
    }

    @PostMapping("/create")
    public ResponseEntity<CreateUserAccountResponse> create(
            @Valid @RequestBody CreateUserAccountRequest request,
            @AuthenticationPrincipal AuthenticatedEntraUser authenticatedUser) {
        return ResponseEntity.status(201).body(handler.handle(mapper.toCommand(request), authenticatedUser));
    }
}

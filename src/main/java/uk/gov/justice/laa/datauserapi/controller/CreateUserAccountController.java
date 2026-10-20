package uk.gov.justice.laa.datauserapi.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.datauserapi.command.CreateUserAccountCommand;
import uk.gov.justice.laa.datauserapi.command.CreateUserAccountResult;
import uk.gov.justice.laa.datauserapi.service.UserAccountCreationService;

@RestController
@RequestMapping("/api/v1/commands/users")
public class CreateUserAccountController {
    private final UserAccountCreationService service;

    public CreateUserAccountController(UserAccountCreationService service) {
        this.service = service;
    }

    @PostMapping("/create")
    @PreAuthorize("hasAuthority('CREATE_EXTERNAL_USER')")
    public ResponseEntity<CreateUserAccountResult> create(
            @Valid @RequestBody CreateUserAccountCommand command,
            @AuthenticationPrincipal Jwt jwt) {
        String actorId = jwt.getClaimAsString("oid");
        if (actorId == null || actorId.isBlank()) {
            actorId = jwt.getSubject();
        }
        return ResponseEntity.status(201).body(service.create(command, actorId));
    }
}

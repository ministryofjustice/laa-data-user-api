package uk.gov.justice.laa.datauserapi.application.command.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.command.service.UserAccountCreationService;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.CreateUserAccountCommand;
import uk.gov.justice.laa.datauserapi.contracts.response.CreateUserAccountResponse;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;

@Component
public class CreateUserAccountHandler implements CommandHandler<CreateUserAccountCommand, CreateUserAccountResponse> {
    private final UserAccountCreationService service;

    public CreateUserAccountHandler(UserAccountCreationService service) {
        this.service = service;
    }

    @Override
    // Keep Tech Services registration outside a local DB transaction; the service opens one for local writes.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CreateUserAccountResponse handle(CreateUserAccountCommand command, AuthenticatedEntraUser actor) {
        return service.create(command, actor.entraOid());
    }
}

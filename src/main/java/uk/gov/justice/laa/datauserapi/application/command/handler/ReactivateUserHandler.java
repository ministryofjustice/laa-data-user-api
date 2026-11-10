package uk.gov.justice.laa.datauserapi.application.command.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.command.service.ReactivateUserService;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.ReactivateUserCommand;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.exception.TechServicesClientException;

@Component
public class ReactivateUserHandler implements CommandHandler<ReactivateUserCommand> {

    private final ReactivateUserService service;

    public ReactivateUserHandler(ReactivateUserService service) {
        this.service = service;
    }

    @Override
    @Transactional(rollbackFor = TechServicesClientException.class)
    public CommandResult handle(ReactivateUserCommand command, AuthenticatedEntraUser actor) {
        return service.reactivate(command, actor);
    }
}

package uk.gov.justice.laa.datauserapi.application.command.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.command.service.UpdateUserDetailsService;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.UpdateUserDetailsCommand;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.exception.TechServicesClientException;

@Component
public class UpdateUserDetailsHandler implements CommandHandler<UpdateUserDetailsCommand> {

    private final UpdateUserDetailsService service;

    public UpdateUserDetailsHandler(UpdateUserDetailsService service) {
        this.service = service;
    }

    @Override
    @Transactional(rollbackFor = TechServicesClientException.class)
    public CommandResult handle(UpdateUserDetailsCommand command, AuthenticatedEntraUser actor) {
        return service.update(command, actor);
    }
}

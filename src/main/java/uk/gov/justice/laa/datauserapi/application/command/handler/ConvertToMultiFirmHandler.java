package uk.gov.justice.laa.datauserapi.application.command.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.command.service.ConvertToMultiFirmService;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.convertmultifirm.ConvertToMultiFirmCommand;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;

@Component
public class ConvertToMultiFirmHandler implements CommandHandler<ConvertToMultiFirmCommand, CommandResult> {

    private final ConvertToMultiFirmService service;

    public ConvertToMultiFirmHandler(ConvertToMultiFirmService service) {
        this.service = service;
    }

    @Override
    @Transactional
    public CommandResult handle(ConvertToMultiFirmCommand command, AuthenticatedEntraUser actor) {
        return service.convert(command, actor);
    }
}

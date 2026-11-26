package uk.gov.justice.laa.datauserapi.application.command.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.command.service.SetActiveProfileService;
import uk.gov.justice.laa.datauserapi.application.command.userprofile.SetActiveProfileCommand;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;

@Component
public class SetActiveProfileHandler implements CommandHandler<SetActiveProfileCommand, CommandResult> {

    private final SetActiveProfileService service;

    public SetActiveProfileHandler(SetActiveProfileService service) {
        this.service = service;
    }

    @Override
    @Transactional
    public CommandResult handle(SetActiveProfileCommand command, AuthenticatedEntraUser actor) {
        return service.setActiveProfile(command, actor);
    }
}

package uk.gov.justice.laa.datauserapi.application.command.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.command.service.UserProfileOfficeService;
import uk.gov.justice.laa.datauserapi.application.command.userprofile.UpdateUserProfileOfficesCommand;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;

@Component
public class UpdateUserProfileOfficesHandler implements CommandHandler<UpdateUserProfileOfficesCommand, CommandResult> {

    private final UserProfileOfficeService service;

    public UpdateUserProfileOfficesHandler(UserProfileOfficeService service) {
        this.service = service;
    }

    @Override
    @Transactional
    public CommandResult handle(UpdateUserProfileOfficesCommand command, AuthenticatedEntraUser actor) {
        return service.updateOffices(command, actor);
    }
}

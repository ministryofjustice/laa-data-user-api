package uk.gov.justice.laa.datauserapi.application.command.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.command.service.UserProfileOfficeService;
import uk.gov.justice.laa.datauserapi.application.command.userprofile.UpdateUserProfileUnrestrictedOfficesCommand;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;

@Component
public class UpdateUserProfileUnrestrictedOfficesHandler
        implements CommandHandler<UpdateUserProfileUnrestrictedOfficesCommand> {

    private final UserProfileOfficeService service;

    public UpdateUserProfileUnrestrictedOfficesHandler(UserProfileOfficeService service) {
        this.service = service;
    }

    @Override
    @Transactional
    public CommandResult handle(UpdateUserProfileUnrestrictedOfficesCommand command, AuthenticatedEntraUser actor) {
        return service.updateUnrestrictedAccess(command, actor);
    }
}

package uk.gov.justice.laa.datauserapi.application.command.handler;

import org.springframework.stereotype.Component;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.command.service.ProfileRoleChangeService;
import uk.gov.justice.laa.datauserapi.application.command.userprofile.ProfileRoleChangeCommand;
import uk.gov.justice.laa.datauserapi.contracts.response.UpdateAppRolesResponse;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.exception.ProfileRoleConflictException;

@Component
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class ProfileRoleChangeHandler implements CommandHandler<ProfileRoleChangeCommand, UpdateAppRolesResponse> {
    private final ProfileRoleChangeService service;

    public ProfileRoleChangeHandler(ProfileRoleChangeService service) {
        this.service = service;
    }

    @Override
    public UpdateAppRolesResponse handle(ProfileRoleChangeCommand command, AuthenticatedEntraUser actor) {
        try {
            return service.change(command);
        } catch (ConcurrencyFailureException exception) {
            throw new ProfileRoleConflictException(exception);
        }
    }
}

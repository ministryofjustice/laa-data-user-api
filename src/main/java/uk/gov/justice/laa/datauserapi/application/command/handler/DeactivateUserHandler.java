package uk.gov.justice.laa.datauserapi.application.command.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.command.service.DeactivateUserService;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.DeactivateUserCommand;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.exception.TechServicesClientException;

@Component
public class DeactivateUserHandler implements CommandHandler<DeactivateUserCommand> {

    private final DeactivateUserService deactivateUserService;

    public DeactivateUserHandler(DeactivateUserService deactivateUserService) {
        this.deactivateUserService = deactivateUserService;
    }

    @Override
    @Transactional(rollbackFor = TechServicesClientException.class)
    public CommandResult handle(DeactivateUserCommand command, AuthenticatedEntraUser actor) {
        return deactivateUserService.deactivate(command.userEntraObjectId(), command.deactivateReason(), actor);
    }
}

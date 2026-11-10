package uk.gov.justice.laa.datauserapi.application.command.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.command.service.DeleteUserService;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.DeleteUserCommand;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;

/**
 * Permission ({@code DELETE_EXTERNAL_USER}), self/internal-target rules and firm scoping are enforced declaratively on
 * {@link DeleteUserCommand} via {@code @RequiresPermission} and its policies before this handler runs.
 */
@Component
public class DeleteUserHandler implements CommandHandler<DeleteUserCommand> {

    private final DeleteUserService deleteUserService;

    public DeleteUserHandler(DeleteUserService deleteUserService) {
        this.deleteUserService = deleteUserService;
    }

    @Override
    @Transactional
    public CommandResult handle(DeleteUserCommand command, AuthenticatedEntraUser actor) {
        return deleteUserService.delete(command, actor);
    }
}

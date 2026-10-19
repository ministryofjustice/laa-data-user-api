package uk.gov.justice.laa.datauserapi.application.command.handler;

import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;

public interface CommandHandler<C> {
    CommandResult handle(C command, AuthenticatedEntraUser actor);
}

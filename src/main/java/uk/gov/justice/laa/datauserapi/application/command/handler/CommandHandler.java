package uk.gov.justice.laa.datauserapi.application.command.handler;

import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;

public interface CommandHandler<C> {
    CommandResult handle(C command, String actorId);
}

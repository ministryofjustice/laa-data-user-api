package uk.gov.justice.laa.datauserapi.application.command.handler;

import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;

public interface CommandHandler<C, R> {
    R handle(C command, AuthenticatedEntraUser actor);
}

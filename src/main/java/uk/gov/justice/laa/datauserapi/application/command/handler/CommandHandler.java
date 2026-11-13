package uk.gov.justice.laa.datauserapi.application.command.handler;

import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.security.rbac.AuthorizationEntryPoint;

public interface CommandHandler<C, R> extends AuthorizationEntryPoint {
    R handle(C command, AuthenticatedEntraUser actor);
}

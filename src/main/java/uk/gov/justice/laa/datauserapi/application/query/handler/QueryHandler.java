package uk.gov.justice.laa.datauserapi.application.query.handler;

import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.security.rbac.AuthorizationEntryPoint;

/**
 * Query-side authorisation entry point: the guarded query message is authorised before the handler runs. The actor is
 * passed explicitly, as for {@code CommandHandler}, so the RBAC interceptor always sees the same actor the controller
 * resolved; handlers need not use it.
 */
public interface QueryHandler<Q, R> extends AuthorizationEntryPoint {

    R handle(Q query, AuthenticatedEntraUser actor);
}

package uk.gov.justice.laa.datauserapi.application.query.handler;

import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.security.rbac.AuthorizationEntryPoint;

/** Query-side authorisation entry point: the guarded query message is authorised before the handler runs. */
public interface QueryHandler<Q, R> extends AuthorizationEntryPoint {

    R handle(Q query, AuthenticatedEntraUser actor);
}

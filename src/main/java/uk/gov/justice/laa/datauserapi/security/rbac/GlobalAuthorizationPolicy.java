package uk.gov.justice.laa.datauserapi.security.rbac;

import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;

/**
 * A cross-cutting {@link AuthorizationPolicy} applied automatically to every guarded message for which
 * {@link #appliesTo} is true, without being listed in {@link RequiresPermission#policies()}.
 *
 * <p>Use for rules that hold across many actions (e.g. firm scoping for Provider Admins). Global policies
 * run after the permission check and before any message-specific policies. A global policy is skipped
 * when its type argument does not match the message.
 */
public interface GlobalAuthorizationPolicy<M> extends AuthorizationPolicy<M> {

    boolean appliesTo(M message, AuthenticatedEntraUser actor);
}

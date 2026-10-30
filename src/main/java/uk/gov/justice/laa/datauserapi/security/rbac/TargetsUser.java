package uk.gov.justice.laa.datauserapi.security.rbac;

import java.util.UUID;

/**
 * Capability for messages that act on a single target user, allowing reusable
 * {@link AuthorizationPolicy}s (e.g. same-firm checks) to be shared across commands and queries.
 */
public interface TargetsUser {

    UUID targetUserEntraObjectId();
}

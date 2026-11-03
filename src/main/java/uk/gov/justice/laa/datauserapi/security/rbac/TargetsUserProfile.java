package uk.gov.justice.laa.datauserapi.security.rbac;

import java.util.UUID;

/**
 * Capability for messages that act on a single user profile, allowing reusable
 * {@link AuthorizationPolicy}s (e.g. same-firm checks) to be shared across commands.
 */
public interface TargetsUserProfile {

    UUID targetUserProfileId();
}

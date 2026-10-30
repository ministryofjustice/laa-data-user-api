package uk.gov.justice.laa.datauserapi.security.rbac;

/**
 * Capability for messages that act on every user of a single firm, allowing reusable
 * {@link AuthorizationPolicy}s (e.g. own-firm checks) to be shared across commands.
 */
public interface TargetsFirm {

    String targetFirmCode();
}

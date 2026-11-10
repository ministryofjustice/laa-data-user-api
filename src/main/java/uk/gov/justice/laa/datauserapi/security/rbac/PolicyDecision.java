package uk.gov.justice.laa.datauserapi.security.rbac;

/**
 * Outcome of an {@link AuthorizationPolicy}. The reason is logged for denials but never returned
 * to the caller.
 */
public record PolicyDecision(boolean granted, PolicyDenialReason reason) {

    private static final PolicyDecision ALLOW = new PolicyDecision(true, null);
    public static final PolicyDecision DENY = new PolicyDecision(false, PolicyDenialReason.POLICY_DENIED);

    public PolicyDecision {
        if (granted == (reason != null)) {
            throw new IllegalArgumentException("Only denied decisions must have a reason");
        }
    }

    public static PolicyDecision allow() {
        return ALLOW;
    }

    public static PolicyDecision deny(PolicyDenialReason reason) {
        return new PolicyDecision(false, reason);
    }
}

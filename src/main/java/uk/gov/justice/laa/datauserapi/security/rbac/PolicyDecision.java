package uk.gov.justice.laa.datauserapi.security.rbac;

/**
 * Outcome of an {@link AuthorizationPolicy}. The reason is logged for denials but never returned
 * to the caller.
 */
public record PolicyDecision(boolean granted, String reason) {

    private static final PolicyDecision ALLOW = new PolicyDecision(true, null);
    public static final PolicyDecision DENY = new PolicyDecision(false, "policy denied");

    public static PolicyDecision allow() {
        return ALLOW;
    }

    public static PolicyDecision deny(String reason) {
        return new PolicyDecision(false, reason);
    }
}

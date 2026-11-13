package uk.gov.justice.laa.datauserapi.security.rbac;

import org.springframework.security.authorization.AuthorizationResult;

/**
 * The denied result of an {@link AuthorizationPolicy}. Spring Security carries it on the resulting
 * {@link org.springframework.security.authorization.AuthorizationDeniedException}, so the handling boundary can
 * log the typed reason while returning a generic 403. The reason is never returned to clients.
 */
public record PolicyAuthorizationDenial(PolicyDenialReason reason) implements AuthorizationResult {

    @Override
    public boolean isGranted() {
        return false;
    }
}

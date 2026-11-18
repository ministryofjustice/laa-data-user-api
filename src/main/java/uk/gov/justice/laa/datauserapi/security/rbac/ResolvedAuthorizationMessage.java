package uk.gov.justice.laa.datauserapi.security.rbac;

/**
 * An immutable message enriched by the interceptor, never by HTTP request binding.
 * The matching policy must authorise fresh resolver input before it is passed to the handler.
 */
public interface ResolvedAuthorizationMessage<C> {
    Class<C> authorizationContextType();

    Class<? extends AuthorizationPolicy<C>> contextPolicyType();

    ResolvedAuthorizationMessage<C> withAuthorizationContext(C context);
}

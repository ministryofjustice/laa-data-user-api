package uk.gov.justice.laa.datauserapi.security.rbac;

/**
 * Loads trusted facts for a policy without coupling the policy's evaluator to persistence.
 * Resolvers run after permission and global-policy checks, not during request binding.
 */
public interface AuthorizationPolicyInputResolver<M, C> {

    Class<? extends AuthorizationPolicy<C>> policyType();

    Class<M> messageType();

    C resolve(M message);
}

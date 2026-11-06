package uk.gov.justice.laa.datauserapi.security.rbac;

public interface AuthorizationPolicyInputResolver<T> {
    Object resolveFor(T input);
}

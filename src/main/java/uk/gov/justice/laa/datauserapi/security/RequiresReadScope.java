package uk.gov.justice.laa.datauserapi.security;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Requires the caller's JWT to carry the {@code user_data.read} scope
 * (or {@code user_data.admin}, which is a superset of read).
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("hasAnyAuthority('SCOPE_user_data.read', 'SCOPE_user_data.admin')")
public @interface RequiresReadScope {
}

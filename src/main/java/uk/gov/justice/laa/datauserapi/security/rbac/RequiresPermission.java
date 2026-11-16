package uk.gov.justice.laa.datauserapi.security.rbac;

import uk.gov.justice.laa.datauserapi.model.Permission;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the {@link Permission}s an actor must hold to dispatch a command or query.
 *
 * <p>Place this on the command/query message type (e.g. {@code DeleteUserCommand}). Any handler
 * method that accepts the annotated message is automatically gated by
 * {@link RequiresPermissionAuthorizationManager} before the handler (and its transaction) runs.
 * Callers lacking the permission, or rejected by any of the declared {@link #policies()}, receive an
 * {@link org.springframework.security.access.AccessDeniedException} (HTTP 403).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresPermission {

    Permission[] value();

    Match match() default Match.ANY;

    /**
     * Fine-grained policies evaluated (in order) once the permission check passes. All must allow.
     * Each policy's type argument must be assignable from the annotated message.
     */
    Class<? extends AuthorizationPolicy>[] policies() default {};

    enum Match {
        /** Actor must hold at least one of the listed permissions. */
        ANY,
        /** Actor must hold every listed permission. */
        ALL
    }
}

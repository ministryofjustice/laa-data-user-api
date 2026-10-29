package uk.gov.justice.laa.datauserapi.security.rbac;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Applies authorisation policies to a command or query without requiring a specific permission.
 *
 * <p>Use this when an action is not yet permission-gated but must still be subject to the applicable
 * {@link GlobalAuthorizationPolicy}s (e.g. Provider Admin firm scoping for messages that
 * {@link TargetsUser}) and any {@link #policies()} declared here. Prefer {@link RequiresPermission}
 * when a permission applies; do not use both on the same message.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PolicyGuarded {

    /** Message-specific policies evaluated after the global policies. All must allow. */
    Class<? extends AuthorizationPolicy>[] policies() default {};
}

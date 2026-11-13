package uk.gov.justice.laa.datauserapi.security.rbac;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks an OPA policy that replaces the legacy policies for its guarded message. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface OpaReplacement {
}

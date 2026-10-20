package uk.gov.justice.laa.datauserapi.security.rbac;

import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.model.Permission;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Generic RBAC gate for command/query handlers.
 *
 * <p>Finds the handler parameter whose type is annotated with {@link RequiresPermission} or
 * {@link PolicyGuarded}, resolves the
 * acting user (an {@link AuthenticatedEntraUser} argument, otherwise the authenticated principal), checks
 * the actor's {@link ActorPermissions} against the declared requirement, then evaluates applicable
 * {@link GlobalAuthorizationPolicy}s followed by any declared {@link AuthorizationPolicy}s. Fails closed on
 * any ambiguity.
 */
@Slf4j
public class RequiresPermissionAuthorizationManager implements AuthorizationManager<MethodInvocation> {

    private static final AuthorizationDecision GRANTED = new AuthorizationDecision(true);
    private static final AuthorizationDecision DENIED = new AuthorizationDecision(false);

    @SuppressWarnings("rawtypes")
    private final Function<Class<? extends AuthorizationPolicy>, AuthorizationPolicy<?>> policyResolver;
    private final Supplier<List<GlobalAuthorizationPolicy<?>>> globalPolicies;

    @SuppressWarnings("rawtypes")
    public RequiresPermissionAuthorizationManager(
            Function<Class<? extends AuthorizationPolicy>, AuthorizationPolicy<?>> policyResolver,
            Supplier<List<GlobalAuthorizationPolicy<?>>> globalPolicies) {
        this.policyResolver = policyResolver;
        this.globalPolicies = globalPolicies;
    }

    @Override
    public AuthorizationResult authorize(Supplier<? extends Authentication> authentication, MethodInvocation invocation) {
        Class<?> targetClass = invocation.getThis() != null ? AopUtils.getTargetClass(invocation.getThis()) : null;
        Optional<GuardedParameter> guarded = findGuardedParameter(invocation.getMethod(), targetClass);
        if (guarded.isEmpty()) {
            return GRANTED;
        }

        String operation = invocation.getMethod().getDeclaringClass().getSimpleName();
        AuthenticatedEntraUser actor = resolveActor(invocation.getArguments(), authentication);
        if (actor == null) {
            log.warn("RBAC denied {}: no authenticated actor", operation);
            return DENIED;
        }

        Requirement required = guarded.get().requirement();
        if (!hasRequiredPermissions(actor, required)) {
            log.warn("RBAC denied actor {} on {}: requires {} of {}",
                    actor.entraOid(), operation, required.match(), Arrays.toString(required.permissions()));
            return DENIED;
        }

        Object message = invocation.getArguments()[guarded.get().index()];
        return evaluatePolicies(required, message, actor, operation) ? GRANTED : DENIED;
    }

    private static boolean hasRequiredPermissions(AuthenticatedEntraUser actor, Requirement required) {
        if (required.permissions().length == 0) {
            return true;
        }
        Set<Permission> held = ActorPermissions.of(actor);
        return switch (required.match()) {
            case ANY -> Arrays.stream(required.permissions()).anyMatch(held::contains);
            case ALL -> Arrays.stream(required.permissions()).allMatch(held::contains);
        };
    }

    private boolean evaluatePolicies(Requirement required, Object message,
                                     AuthenticatedEntraUser actor, String operation) {
        if (message == null) {
            boolean anyPolicies = required.policies().length > 0 || !globalPolicies.get().isEmpty();
            if (anyPolicies) {
                log.warn("RBAC denied actor {} on {}: message is null, policies cannot be evaluated",
                        actor.entraOid(), operation);
            }
            return !anyPolicies;
        }
        return evaluateGlobalPolicies(message, actor, operation)
                && evaluateDeclaredPolicies(required, message, actor, operation);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private boolean evaluateGlobalPolicies(Object message, AuthenticatedEntraUser actor, String operation) {
        for (GlobalAuthorizationPolicy policy : globalPolicies.get()) {
            if (supports(policy, message) && policy.appliesTo(message, actor)
                    && !isGranted(policy, policy.getClass(), message, actor, operation)) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private boolean evaluateDeclaredPolicies(Requirement required, Object message,
                                             AuthenticatedEntraUser actor, String operation) {
        for (Class<? extends AuthorizationPolicy> policyType : required.policies()) {
            AuthorizationPolicy policy = policyResolver.apply(policyType);
            if (!supports(policy, message)) {
                log.error("RBAC misconfiguration on {}: policy {} does not support message {}",
                        operation, policyType.getSimpleName(), message.getClass().getSimpleName());
                return false;
            }
            if (!isGranted(policy, policyType, message, actor, operation)) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean isGranted(AuthorizationPolicy policy, Class<?> policyType, Object message,
                                     AuthenticatedEntraUser actor, String operation) {
        PolicyDecision decision = policy.evaluate(message, actor);
        if (decision == null || !decision.granted()) {
            log.warn("RBAC denied actor {} on {} by {}: {}", actor.entraOid(), operation,
                    AopUtils.getTargetClass(policy).getSimpleName(), decision == null ? "no decision" : decision.reason());
            return false;
        }
        return true;
    }

    private static boolean supports(AuthorizationPolicy<?> policy, Object message) {
        Class<?> supported = ResolvableType.forClass(AuthorizationPolicy.class, AopUtils.getTargetClass(policy))
                .resolveGeneric(0);
        return supported == null || supported.isInstance(message);
    }

    /**
     * Returns the requirement declared via {@link RequiresPermission} or {@link PolicyGuarded} on any parameter
     * type of the (bridge-resolved, most specific) method. Used both by the pointcut (static) and at invocation time.
     */
    static Optional<Requirement> findRequirement(Method method, Class<?> targetClass) {
        return findGuardedParameter(method, targetClass).map(GuardedParameter::requirement);
    }

    private static Optional<GuardedParameter> findGuardedParameter(Method method, Class<?> targetClass) {
        Method specific = BridgeMethodResolver.findBridgedMethod(AopUtils.getMostSpecificMethod(method, targetClass));
        Class<?>[] types = specific.getParameterTypes();
        for (int i = 0; i < types.length; i++) {
            Optional<Requirement> requirement = Requirement.of(types[i]);
            if (requirement.isPresent()) {
                return Optional.of(new GuardedParameter(i, requirement.get()));
            }
        }
        return Optional.empty();
    }

    private static AuthenticatedEntraUser resolveActor(Object[] args, Supplier<? extends Authentication> authentication) {
        for (Object arg : args) {
            if (arg instanceof AuthenticatedEntraUser actor) {
                return actor;
            }
        }
        Authentication auth = authentication.get();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedEntraUser principal) {
            return principal;
        }
        return null;
    }

    private record GuardedParameter(int index, Requirement requirement) {
    }

    /** Normalised view of {@link RequiresPermission} / {@link PolicyGuarded}; no permissions means policies only. */
    @SuppressWarnings("rawtypes")
    record Requirement(Permission[] permissions, RequiresPermission.Match match,
                       Class<? extends AuthorizationPolicy>[] policies) {

        static Optional<Requirement> of(Class<?> messageType) {
            RequiresPermission permission = AnnotatedElementUtils.findMergedAnnotation(messageType, RequiresPermission.class);
            PolicyGuarded policyGuarded = AnnotatedElementUtils.findMergedAnnotation(messageType, PolicyGuarded.class);
            if (permission != null && policyGuarded != null) {
                throw new IllegalStateException(messageType.getName()
                        + " must declare only one of @RequiresPermission or @PolicyGuarded");
            }
            if (permission != null) {
                if (permission.value().length == 0) {
                    throw new IllegalStateException(messageType.getName()
                            + " declares @RequiresPermission without permissions; use @PolicyGuarded instead");
                }
                return Optional.of(new Requirement(permission.value(), permission.match(), permission.policies()));
            }
            if (policyGuarded != null) {
                return Optional.of(new Requirement(new Permission[0], RequiresPermission.Match.ALL, policyGuarded.policies()));
            }
            return Optional.empty();
        }
    }
}

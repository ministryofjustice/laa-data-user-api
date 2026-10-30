package uk.gov.justice.laa.datauserapi.security.rbac;

import static net.logstash.logback.argument.StructuredArguments.kv;

import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.NonNull;
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

    private static final String EVENT = "event";
    private static final String AUTHORIZATION_DENIED = "authorization_denied";
    private static final String OPERATION = "operation";
    private static final String ACTOR_ENTRA_OID = "actorEntraOid";
    private static final String POLICY = "policy";

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
    public AuthorizationResult authorize(@NonNull Supplier<? extends Authentication> authentication, MethodInvocation invocation) {
        assert invocation != null;
        Class<?> targetClass = invocation.getThis() != null ? AopUtils.getTargetClass(invocation.getThis()) : null;
        Optional<GuardedParameter> guarded = findGuardedParameter(invocation.getMethod(), targetClass);
        if (guarded.isEmpty()) {
            return GRANTED;
        }

        String operation = invocation.getMethod().getDeclaringClass().getSimpleName();
        AuthenticatedEntraUser actor = resolveActor(invocation.getArguments(), authentication);
        if (actor == null) {
            log.info("RBAC authorization denied: no authenticated actor",
                    kv(EVENT, AUTHORIZATION_DENIED),
                    kv(OPERATION, operation));
            return DENIED;
        }

        Requirement required = guarded.get().requirement();
        if (!hasRequiredPermissions(actor, required)) {
            log.info("RBAC authorization denied: required permissions not held",
                    kv(EVENT, AUTHORIZATION_DENIED),
                    kv(ACTOR_ENTRA_OID, actor.entraOid()),
                    kv(OPERATION, operation),
                    kv("permissionMatch", required.match()),
                    kv("requiredPermissions", Arrays.toString(required.permissions())));
            return DENIED;
        }

        Object obj = invocation.getArguments()[guarded.get().index()];
        return evaluatePolicies(required, obj, actor, operation) ? GRANTED : DENIED;
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

    private boolean evaluatePolicies(Requirement required, Object obj,
                                     AuthenticatedEntraUser actor, String operation) {
        if (obj == null) {
            boolean anyPolicies = required.policies().length > 0 || !globalPolicies.get().isEmpty();
            if (anyPolicies) {
                log.info("RBAC authorization denied: object is null and policies cannot be evaluated",
                        kv(EVENT, AUTHORIZATION_DENIED),
                        kv(ACTOR_ENTRA_OID, actor.entraOid()),
                        kv(OPERATION, operation));
            }
            return !anyPolicies;
        }
        return evaluateGlobalPolicies(obj, actor, operation)
                && evaluateDeclaredPolicies(required, obj, actor, operation);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private boolean evaluateGlobalPolicies(Object obj, AuthenticatedEntraUser actor, String operation) {
        for (GlobalAuthorizationPolicy policy : globalPolicies.get()) {
            if (supports(policy, obj) && policy.appliesTo(obj, actor)
                    && !isGranted(policy, policy.getClass(), obj, actor, operation)) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings({"rawtypes"})
    private boolean evaluateDeclaredPolicies(Requirement required, Object obj,
                                             AuthenticatedEntraUser actor, String operation) {
        for (Class<? extends AuthorizationPolicy> policyType : required.policies()) {
            AuthorizationPolicy policy = policyResolver.apply(policyType);
            if (!supports(policy, obj)) {
                log.error("RBAC authorization misconfiguration: policy does not support object",
                        kv(EVENT, "authorization_configuration_error"),
                        kv(OPERATION, operation),
                        kv(POLICY, policyType.getSimpleName()),
                        kv("objectType", obj.getClass().getSimpleName()));
                return false;
            }
            if (!isGranted(policy, policyType, obj, actor, operation)) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean isGranted(AuthorizationPolicy policy, Class<?> policyType, Object obj,
                                     AuthenticatedEntraUser actor, String operation) {
        PolicyDecision decision = policy.evaluate(obj, actor);
        if (!decision.granted()) {
            log.info("RBAC authorization denied by policy",
                    kv(EVENT, AUTHORIZATION_DENIED),
                    kv(ACTOR_ENTRA_OID, actor.entraOid()),
                    kv(OPERATION, operation),
                    kv(POLICY, AopUtils.getTargetClass(policy).getSimpleName()),
                    kv("reason", decision.reason()));
            return false;
        }
        return true;
    }

    private static boolean supports(AuthorizationPolicy<?> policy, Object obj) {
        Class<?> supported = ResolvableType.forClass(AuthorizationPolicy.class, AopUtils.getTargetClass(policy))
                .resolveGeneric(0);
        return supported == null || supported.isInstance(obj);
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

        static Optional<Requirement> of(Class<?> objType) {
            RequiresPermission permission = AnnotatedElementUtils.findMergedAnnotation(objType, RequiresPermission.class);
            PolicyGuarded policyGuarded = AnnotatedElementUtils.findMergedAnnotation(objType, PolicyGuarded.class);
            if (permission != null && policyGuarded != null) {
                throw new InvalidAuthorizationConfigurationException(objType.getName()
                        + " must declare only one of @RequiresPermission or @PolicyGuarded");
            }
            if (permission != null) {
                if (permission.value().length == 0) {
                    throw new InvalidAuthorizationConfigurationException(objType.getName()
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

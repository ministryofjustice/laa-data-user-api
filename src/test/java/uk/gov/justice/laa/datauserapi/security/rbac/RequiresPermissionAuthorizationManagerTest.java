package uk.gov.justice.laa.datauserapi.security.rbac;

import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.model.Permission;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RequiresPermissionAuthorizationManagerTest {

    private final Map<Class<?>, AuthorizationPolicy<?>> policies = new HashMap<>();
    private final List<GlobalAuthorizationPolicy<?>> globals = new ArrayList<>();
    private final RequiresPermissionAuthorizationManager manager =
            new RequiresPermissionAuthorizationManager(policies::get, () -> globals);
    private final Supplier<Authentication> noAuth = () -> null;

    @RequiresPermission(Permission.DELETE_EXTERNAL_USER)
    record AnyCommand() {}

    @RequiresPermission(value = {Permission.VIEW_EXTERNAL_USER, Permission.EDIT_EXTERNAL_USER},
            match = RequiresPermission.Match.ALL)
    record AllCommand() {}

    record UnguardedCommand() {}

    @RequiresPermission(value = Permission.DELETE_EXTERNAL_USER, policies = {AllowPolicy.class, TargetPolicy.class})
    record PolicyCommand(UUID targetUserEntraObjectId) implements TargetsUser {}

    @RequiresPermission(value = Permission.DELETE_EXTERNAL_USER, policies = TargetPolicy.class)
    record MisconfiguredCommand() {}

    static class AllowPolicy implements AuthorizationPolicy<Object> {
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public @NonNull PolicyDecision evaluate(Object message, AuthenticatedEntraUser actor) {
            calls.incrementAndGet();
            return PolicyDecision.allow();
        }
    }

    static class TargetPolicy implements AuthorizationPolicy<TargetsUser> {
        UUID allowedTarget;

        @Override
        public @NonNull PolicyDecision evaluate(TargetsUser message, AuthenticatedEntraUser actor) {
            return message.targetUserEntraObjectId().equals(allowedTarget)
                    ? PolicyDecision.allow() : PolicyDecision.deny("not allowed");
        }
    }

    /** Applies only to {@link TargetsUser} messages and only when the actor is EXTERNAL. */
    static class ExternalOnlyGlobalPolicy implements GlobalAuthorizationPolicy<TargetsUser> {
        final AtomicInteger calls = new AtomicInteger();
        boolean grant;

        @Override
        public boolean appliesTo(TargetsUser message, AuthenticatedEntraUser actor) {
            return actor.isExternalUser();
        }

        @Override
        public @NonNull PolicyDecision evaluate(TargetsUser message, AuthenticatedEntraUser actor) {
            calls.incrementAndGet();
            return grant ? PolicyDecision.allow() : PolicyDecision.deny("global denied");
        }
    }

    @PolicyGuarded
    record PolicyOnlyCommand(UUID targetUserEntraObjectId) implements TargetsUser {}

    @PolicyGuarded(policies = TargetPolicy.class)
    record PolicyOnlyWithDeclaredCommand(UUID targetUserEntraObjectId) implements TargetsUser {}

    @RequiresPermission(value = Permission.DELETE_EXTERNAL_USER)
    @PolicyGuarded
    record ConflictingCommand() {}

    static class Handlers {
        public void any(AnyCommand c, AuthenticatedEntraUser actor) {
        }

        public void all(AllCommand c, AuthenticatedEntraUser actor) {
        }

        public void query(AnyCommand q) {
        }

        public void unguarded(UnguardedCommand c, AuthenticatedEntraUser actor) {
        }

        public void policy(AuthenticatedEntraUser actor, PolicyCommand c) {
        }

        public void misconfigured(MisconfiguredCommand c, AuthenticatedEntraUser actor) {
        }

        public void policyOnly(PolicyOnlyCommand c, AuthenticatedEntraUser actor) {
        }

        public void policyOnlyWithDeclared(PolicyOnlyWithDeclaredCommand c, AuthenticatedEntraUser actor) {
        }

        public void conflicting(ConflictingCommand c, AuthenticatedEntraUser actor) {
        }
    }

    @Test
    void grantsWhenActorArgumentHoldsPermission() throws Exception {
        var actor = RbacTestActors.actorWith(Permission.DELETE_EXTERNAL_USER);
        assertThat(decide("any", new Object[]{new AnyCommand(), actor}, noAuth)).isTrue();
    }

    @Test
    void deniesWhenActorArgumentLacksPermission() throws Exception {
        var actor = RbacTestActors.actorWith(Permission.VIEW_EXTERNAL_USER);
        assertThat(decide("any", new Object[]{new AnyCommand(), actor}, noAuth)).isFalse();
    }

    @Test
    void allMatchRequiresEveryPermission() throws Exception {
        var partial = RbacTestActors.actorWith(Permission.VIEW_EXTERNAL_USER);
        var full = RbacTestActors.actorWith(Permission.VIEW_EXTERNAL_USER, Permission.EDIT_EXTERNAL_USER);
        assertThat(decide("all", new Object[]{new AllCommand(), partial}, noAuth)).isFalse();
        assertThat(decide("all", new Object[]{new AllCommand(), full}, noAuth)).isTrue();
    }

    @Test
    void fallsBackToAuthenticatedPrincipalWhenNoActorArgument() throws Exception {
        var allowed = RbacTestActors.actorWith(Permission.DELETE_EXTERNAL_USER);
        var denied = RbacTestActors.actorWith();
        assertThat(decide("query", new Object[]{new AnyCommand()},
                () -> new TestingAuthenticationToken(allowed, null))).isTrue();
        assertThat(decide("query", new Object[]{new AnyCommand()},
                () -> new TestingAuthenticationToken(denied, null))).isFalse();
    }

    @Test
    void deniesWhenNoActorCanBeResolved() throws Exception {
        assertThat(decide("query", new Object[]{new AnyCommand()}, noAuth)).isFalse();
        assertThat(decide("query", new Object[]{new AnyCommand()},
                () -> new TestingAuthenticationToken("someone-else", null))).isFalse();
    }

    @Test
    void deniesEvenWhenCommandArgumentIsNull() throws Exception {
        var actor = RbacTestActors.actorWith();
        assertThat(decide("any", new Object[]{null, actor}, noAuth)).isFalse();
    }

    @Test
    void grantsUnguardedMessages() throws Exception {
        assertThat(decide("unguarded", new Object[]{new UnguardedCommand(), RbacTestActors.actorWith()}, noAuth)).isTrue();
    }

    @Test
    void grantsWhenPermissionHeldAndAllPoliciesAllow() throws Exception {
        UUID target = UUID.randomUUID();
        registerPolicies(target);
        var actor = RbacTestActors.actorWith(Permission.DELETE_EXTERNAL_USER);

        assertThat(decide("policy", new Object[]{actor, new PolicyCommand(target)}, noAuth)).isTrue();
    }

    @Test
    void deniesWhenAnyPolicyDenies() throws Exception {
        registerPolicies(UUID.randomUUID());
        var actor = RbacTestActors.actorWith(Permission.DELETE_EXTERNAL_USER);

        assertThat(decide("policy", new Object[]{actor, new PolicyCommand(UUID.randomUUID())}, noAuth)).isFalse();
    }

    @Test
    void doesNotEvaluatePoliciesWhenPermissionMissing() throws Exception {
        UUID target = UUID.randomUUID();
        AllowPolicy allow = registerPolicies(target);
        var actor = RbacTestActors.actorWith(Permission.VIEW_EXTERNAL_USER);

        assertThat(decide("policy", new Object[]{actor, new PolicyCommand(target)}, noAuth)).isFalse();
        assertThat(allow.calls).hasValue(0);
    }

    @Test
    void deniesWhenPolicyDoesNotSupportMessageType() throws Exception {
        registerPolicies(UUID.randomUUID());
        var actor = RbacTestActors.actorWith(Permission.DELETE_EXTERNAL_USER);

        assertThat(decide("misconfigured", new Object[]{new MisconfiguredCommand(), actor}, noAuth)).isFalse();
    }

    @Test
    void deniesWhenMessageIsNullAndPoliciesDeclared() throws Exception {
        registerPolicies(UUID.randomUUID());
        var actor = RbacTestActors.actorWith(Permission.DELETE_EXTERNAL_USER);

        assertThat(decide("policy", new Object[]{actor, null}, noAuth)).isFalse();
    }

    @Test
    void globalPolicyIsAppliedWithoutBeingDeclared() throws Exception {
        UUID target = UUID.randomUUID();
        registerPolicies(target);
        ExternalOnlyGlobalPolicy global = new ExternalOnlyGlobalPolicy();
        globals.add(global);
        var external = RbacTestActors.externalActorWith(UUID.randomUUID(), Permission.DELETE_EXTERNAL_USER);

        assertThat(decide("policy", new Object[]{external, new PolicyCommand(target)}, noAuth)).isFalse();
        global.grant = true;
        assertThat(decide("policy", new Object[]{external, new PolicyCommand(target)}, noAuth)).isTrue();
        assertThat(global.calls).hasValue(2);
    }

    @Test
    void globalPolicySkippedWhenNotApplicableToActor() throws Exception {
        UUID target = UUID.randomUUID();
        registerPolicies(target);
        ExternalOnlyGlobalPolicy global = new ExternalOnlyGlobalPolicy();
        globals.add(global);
        var internal = RbacTestActors.actorWith(Permission.DELETE_EXTERNAL_USER);

        assertThat(decide("policy", new Object[]{internal, new PolicyCommand(target)}, noAuth)).isTrue();
        assertThat(global.calls).hasValue(0);
    }

    @Test
    void globalPolicySkippedWhenMessageTypeNotSupported() throws Exception {
        ExternalOnlyGlobalPolicy global = new ExternalOnlyGlobalPolicy();
        globals.add(global);
        var external = RbacTestActors.externalActorWith(UUID.randomUUID(), Permission.DELETE_EXTERNAL_USER);

        assertThat(decide("any", new Object[]{new AnyCommand(), external}, noAuth)).isTrue();
        assertThat(global.calls).hasValue(0);
    }

    @Test
    void globalPolicyNotEvaluatedWhenPermissionMissing() throws Exception {
        ExternalOnlyGlobalPolicy global = new ExternalOnlyGlobalPolicy();
        global.grant = true;
        globals.add(global);
        var external = RbacTestActors.externalActorWith(UUID.randomUUID(), Permission.VIEW_EXTERNAL_USER);

        assertThat(decide("policy", new Object[]{external, new PolicyCommand(UUID.randomUUID())}, noAuth)).isFalse();
        assertThat(global.calls).hasValue(0);
    }

    @Test
    void deniesNullMessageWhenGlobalPoliciesRegistered() throws Exception {
        globals.add(new ExternalOnlyGlobalPolicy());
        var actor = RbacTestActors.actorWith(Permission.DELETE_EXTERNAL_USER);

        assertThat(decide("any", new Object[]{null, actor}, noAuth)).isFalse();
    }

    @Test
    void policyGuardedRequiresNoPermissionButAppliesGlobalPolicies() throws Exception {
        ExternalOnlyGlobalPolicy global = new ExternalOnlyGlobalPolicy();
        globals.add(global);
        var internalNoPermissions = RbacTestActors.actorWith();
        var externalNoPermissions = RbacTestActors.externalActorWith(UUID.randomUUID());
        var command = new PolicyOnlyCommand(UUID.randomUUID());

        assertThat(decide("policyOnly", new Object[]{command, internalNoPermissions}, noAuth)).isTrue();
        assertThat(decide("policyOnly", new Object[]{command, externalNoPermissions}, noAuth)).isFalse();
        global.grant = true;
        assertThat(decide("policyOnly", new Object[]{command, externalNoPermissions}, noAuth)).isTrue();
    }

    @Test
    void policyGuardedEvaluatesDeclaredPolicies() throws Exception {
        UUID allowed = UUID.randomUUID();
        registerPolicies(allowed);
        var actor = RbacTestActors.actorWith();

        assertThat(decide("policyOnlyWithDeclared", new Object[]{new PolicyOnlyWithDeclaredCommand(allowed), actor}, noAuth)).isTrue();
        assertThat(decide("policyOnlyWithDeclared",
                new Object[]{new PolicyOnlyWithDeclaredCommand(UUID.randomUUID()), actor}, noAuth)).isFalse();
    }

    @Test
    void policyGuardedStillRequiresAnActor() throws Exception {
        assertThat(decide("policyOnly", new Object[]{new PolicyOnlyCommand(UUID.randomUUID()), null}, noAuth)).isFalse();
    }

    @Test
    void rejectsMessageDeclaringBothAnnotations() {
        assertThatThrownBy(() -> decide("conflicting", new Object[]{new ConflictingCommand(), RbacTestActors.actorWith()}, noAuth))
                .isInstanceOf(InvalidAuthorizationConfigurationException.class)
                .hasMessageContaining("only one of");
    }

    private AllowPolicy registerPolicies(UUID allowedTarget) {
        AllowPolicy allow = new AllowPolicy();
        TargetPolicy target = new TargetPolicy();
        target.allowedTarget = allowedTarget;
        policies.put(AllowPolicy.class, allow);
        policies.put(TargetPolicy.class, target);
        return allow;
    }

    private boolean decide(String methodName, Object[] args, Supplier<Authentication> auth) throws Exception {
        Method method = java.util.Arrays.stream(Handlers.class.getMethods())
                .filter(m -> m.getName().equals(methodName)).findFirst().orElseThrow();
        MethodInvocation invocation = mock(MethodInvocation.class);
        when(invocation.getMethod()).thenReturn(method);
        when(invocation.getThis()).thenReturn(new Handlers());
        when(invocation.getArguments()).thenReturn(args);
        return manager.authorize(auth, invocation).isGranted();
    }
}

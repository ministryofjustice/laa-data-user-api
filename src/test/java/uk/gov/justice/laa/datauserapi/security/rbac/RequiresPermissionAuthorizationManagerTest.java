package uk.gov.justice.laa.datauserapi.security.rbac;

import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.model.Permission;
import uk.gov.justice.laa.datauserapi.application.command.dto.UserDeactivationContext;
import uk.gov.justice.laa.datauserapi.application.command.policy.ReactivationHierarchyPolicy;
import uk.gov.justice.laa.datauserapi.exception.UserNotFoundException;
import uk.gov.justice.laa.datauserapi.model.DeactivationType;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private final List<AuthorizationPolicyInputResolver<?, ?>> inputResolvers = new ArrayList<>();
    private final RequiresPermissionAuthorizationManager manager =
            new RequiresPermissionAuthorizationManager(policies::get, () -> globals, () -> inputResolvers);
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

    @RequiresPermission(value = Permission.ENABLE_EXTERNAL_USER, policies = ReactivationHierarchyPolicy.class)
    record ContextCommand(UUID targetUserEntraObjectId) implements TargetsUser {}

    @RequiresPermission(value = Permission.DELETE_EXTERNAL_USER,
            policies = {TargetPolicy.class, OpaFeaturePolicy.class})
    record FlaggedPolicyCommand(UUID targetUserEntraObjectId) implements TargetsUser {}

    static class ContextResolver implements AuthorizationPolicyInputResolver<TargetsUser, UserDeactivationContext> {
        final AtomicInteger calls = new AtomicInteger();
        RuntimeException failure;
        UserDeactivationContext context = new UserDeactivationContext(true, Optional.of(DeactivationType.NONE));

        @Override
        public Class<? extends AuthorizationPolicy<UserDeactivationContext>> policyType() {
            return ReactivationHierarchyPolicy.class;
        }

        @Override
        public Class<TargetsUser> messageType() {
            return TargetsUser.class;
        }

        @Override
        public UserDeactivationContext resolve(TargetsUser message) {
            calls.incrementAndGet();
            if (failure != null) {
                throw failure;
            }
            return context;
        }
    }

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
                    ? PolicyDecision.allow() : PolicyDecision.DENY;
        }
    }

    @OpaReplacement
    static class OpaFeaturePolicy implements AuthorizationPolicy<TargetsUser> {
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public AuthorizationPolicyFeature feature() {
            return AuthorizationPolicyFeature.OPA;
        }

        @Override
        public @NonNull PolicyDecision evaluate(TargetsUser message, AuthenticatedEntraUser actor) {
            calls.incrementAndGet();
            return PolicyDecision.allow();
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
            return grant ? PolicyDecision.allow() : PolicyDecision.DENY;
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

        public void context(ContextCommand c, AuthenticatedEntraUser actor) {
        }

        public void flagged(FlaggedPolicyCommand c, AuthenticatedEntraUser actor) {
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
    void legacyAndOpaPoliciesCanBeEnabledIndependently() throws Exception {
        UUID allowedTarget = UUID.randomUUID();
        TargetPolicy legacyPolicy = new TargetPolicy();
        legacyPolicy.allowedTarget = allowedTarget;
        OpaFeaturePolicy opaPolicy = new OpaFeaturePolicy();
        policies.put(TargetPolicy.class, legacyPolicy);
        policies.put(OpaFeaturePolicy.class, opaPolicy);
        ExternalOnlyGlobalPolicy global = new ExternalOnlyGlobalPolicy();
        globals.add(global);
        var actor = RbacTestActors.externalActorWith(UUID.randomUUID(), Permission.DELETE_EXTERNAL_USER);
        final FlaggedPolicyCommand command = new FlaggedPolicyCommand(UUID.randomUUID());

        RequiresPermissionAuthorizationManager legacyOnly =
                new RequiresPermissionAuthorizationManager(policies::get, () -> globals, () -> inputResolvers,
                        true, false);
        global.grant = true;
        assertThat(decide(legacyOnly, "flagged",
                new Object[]{new FlaggedPolicyCommand(allowedTarget), actor}, noAuth)).isTrue();
        assertThat(opaPolicy.calls).hasValue(0);
        global.calls.set(0);

        RequiresPermissionAuthorizationManager opaOnly =
                new RequiresPermissionAuthorizationManager(policies::get, () -> globals, () -> inputResolvers,
                        false, true);
        assertThat(decide(opaOnly, "flagged", new Object[]{command, actor}, noAuth)).isTrue();
        assertThat(opaPolicy.calls).hasValue(1);
        assertThat(global.calls).hasValue(0);

        RequiresPermissionAuthorizationManager noFineGrainedPolicies =
                new RequiresPermissionAuthorizationManager(policies::get, () -> globals, () -> inputResolvers,
                        false, false);
        assertThat(decide(noFineGrainedPolicies, "flagged", new Object[]{command, actor}, noAuth)).isFalse();
        assertThat(opaPolicy.calls).hasValue(1);
        assertThat(decide(noFineGrainedPolicies, "flagged",
                new Object[]{command, RbacTestActors.externalActorWith(UUID.randomUUID())}, noAuth)).isFalse();
    }

    @Test
    void legacyPoliciesRemainEnabledForCommandsWithoutAnOpaReplacement() throws Exception {
        UUID allowedTarget = UUID.randomUUID();
        final AllowPolicy allow = registerPolicies(allowedTarget);
        ExternalOnlyGlobalPolicy global = new ExternalOnlyGlobalPolicy();
        global.grant = true;
        globals.add(global);
        RequiresPermissionAuthorizationManager opaOnly =
                new RequiresPermissionAuthorizationManager(policies::get, () -> globals, () -> inputResolvers,
                        false, true);
        var actor = RbacTestActors.externalActorWith(UUID.randomUUID(), Permission.DELETE_EXTERNAL_USER);

        assertThat(decide(opaOnly, "policy",
                new Object[]{actor, new PolicyCommand(UUID.randomUUID())}, noAuth)).isFalse();
        assertThat(global.calls).hasValue(1);
        assertThat(allow.calls).hasValue(1);
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

    @Test
    void resolvesTrustedFactsOnceAndEvaluatesPurePolicy() throws Exception {
        ContextResolver resolver = registerContextResolver();
        var actor = RbacTestActors.actorWith(Permission.ENABLE_EXTERNAL_USER);
        assertThat(decide("context", new Object[]{new ContextCommand(UUID.randomUUID()), actor}, noAuth)).isTrue();
        assertThat(resolver.calls).hasValue(1);
        resolver.context = new UserDeactivationContext(false, Optional.empty());
        assertThat(decide("context", new Object[]{new ContextCommand(UUID.randomUUID()), actor}, noAuth)).isFalse();
    }

    @Test
    void doesNotLoadFactsBeforePermissionsOrGlobalPolicyAllows() throws Exception {
        final ContextResolver resolver = registerContextResolver();
        ContextCommand command = new ContextCommand(UUID.randomUUID());
        assertThat(decide("context", new Object[]{command, RbacTestActors.actorWith()}, noAuth)).isFalse();
        globals.add(new ExternalOnlyGlobalPolicy());
        var actor = RbacTestActors.externalActorWith(UUID.randomUUID(), Permission.ENABLE_EXTERNAL_USER);
        assertThat(decide("context", new Object[]{command, actor}, noAuth)).isFalse();
        assertThat(resolver.calls).hasValue(0);
    }

    @Test
    void contextLookupPreservesNotFoundException() {
        ContextResolver resolver = registerContextResolver();
        resolver.failure = new UserNotFoundException("missing");
        var actor = RbacTestActors.actorWith(Permission.ENABLE_EXTERNAL_USER);
        assertThatThrownBy(() -> decide("context", new Object[]{new ContextCommand(UUID.randomUUID()), actor}, noAuth))
                .isSameAs(resolver.failure);
    }

    @Test
    void deniesContextPolicyWhenNoResolverIsRegistered() throws Exception {
        policies.put(ReactivationHierarchyPolicy.class, new ReactivationHierarchyPolicy());
        var actor = RbacTestActors.actorWith(Permission.ENABLE_EXTERNAL_USER);
        assertThat(decide("context", new Object[]{new ContextCommand(UUID.randomUUID()), actor}, noAuth)).isFalse();
    }

    @Test
    void rejectsDuplicateResolversWithoutLoadingFacts() {
        ContextResolver resolver = registerContextResolver();
        inputResolvers.add(new ContextResolver());
        var actor = RbacTestActors.actorWith(Permission.ENABLE_EXTERNAL_USER);
        assertThatThrownBy(() -> decide("context", new Object[]{new ContextCommand(UUID.randomUUID()), actor}, noAuth))
                .isInstanceOf(InvalidAuthorizationConfigurationException.class);
        assertThat(resolver.calls).hasValue(0);
    }

    @Test
    void nullResolvedFactsFailClosed() throws Exception {
        ContextResolver resolver = registerContextResolver();
        resolver.context = null;
        var actor = RbacTestActors.actorWith(Permission.ENABLE_EXTERNAL_USER);
        assertThat(decide("context", new Object[]{new ContextCommand(UUID.randomUUID()), actor}, noAuth)).isFalse();
    }

    private ContextResolver registerContextResolver() {
        ContextResolver resolver = new ContextResolver();
        inputResolvers.add(resolver);
        policies.put(ReactivationHierarchyPolicy.class, new ReactivationHierarchyPolicy());
        return resolver;
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
        return decide(manager, methodName, args, auth);
    }

    private boolean decide(RequiresPermissionAuthorizationManager authorizationManager,
                           String methodName, Object[] args, Supplier<Authentication> auth) throws Exception {
        Method method = java.util.Arrays.stream(Handlers.class.getMethods())
                .filter(m -> m.getName().equals(methodName)).findFirst().orElseThrow();
        MethodInvocation invocation = mock(MethodInvocation.class);
        when(invocation.getMethod()).thenReturn(method);
        when(invocation.getThis()).thenReturn(new Handlers());
        when(invocation.getArguments()).thenReturn(args);
        return authorizationManager.authorize(auth, invocation).isGranted();
    }
}

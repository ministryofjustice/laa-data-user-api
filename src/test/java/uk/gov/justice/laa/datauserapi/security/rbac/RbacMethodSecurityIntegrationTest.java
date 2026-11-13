package uk.gov.justice.laa.datauserapi.security.rbac;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.command.handler.BulkDeactivateFirmUsersHandler;
import uk.gov.justice.laa.datauserapi.application.command.handler.CommandHandler;
import uk.gov.justice.laa.datauserapi.application.command.handler.DeactivateUserHandler;
import uk.gov.justice.laa.datauserapi.application.command.handler.ConvertToMultiFirmHandler;
import uk.gov.justice.laa.datauserapi.application.command.policy.InternalActorOnlyPolicy;
import uk.gov.justice.laa.datauserapi.application.command.service.ConvertToMultiFirmService;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.convertmultifirm.ConvertToMultiFirmCommand;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedAppRole;
import uk.gov.justice.laa.datauserapi.application.command.handler.ReactivateUserHandler;
import uk.gov.justice.laa.datauserapi.application.command.service.ReactivateUserService;
import uk.gov.justice.laa.datauserapi.application.command.handler.UpdateUserDetailsHandler;
import uk.gov.justice.laa.datauserapi.application.command.handler.UpdateUserProfileOfficesHandler;
import uk.gov.justice.laa.datauserapi.application.command.handler.UpdateUserProfileUnrestrictedOfficesHandler;
import uk.gov.justice.laa.datauserapi.application.command.service.UpdateUserDetailsService;
import uk.gov.justice.laa.datauserapi.application.command.service.UserProfileOfficeService;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.UpdateUserDetailsCommand;
import uk.gov.justice.laa.datauserapi.application.command.userprofile.UpdateUserProfileUnrestrictedOfficesCommand;
import uk.gov.justice.laa.datauserapi.application.command.dto.UserDeactivationContext;
import uk.gov.justice.laa.datauserapi.application.command.mapper.BulkDeactivateFirmUsersMapper;
import uk.gov.justice.laa.datauserapi.application.command.service.BulkDeactivateFirmUsersService;
import uk.gov.justice.laa.datauserapi.application.command.service.DeactivateUserService;
import uk.gov.justice.laa.datauserapi.application.command.service.ReactivationPolicyInputResolver;
import uk.gov.justice.laa.datauserapi.application.command.mapper.DeleteUserMapper;
import uk.gov.justice.laa.datauserapi.application.command.policy.ExternalProfileTargetOnlyPolicy;
import uk.gov.justice.laa.datauserapi.application.command.policy.ExternalTargetOnlyPolicy;
import uk.gov.justice.laa.datauserapi.application.command.policy.NotSelfProfileTargetPolicy;
import uk.gov.justice.laa.datauserapi.application.command.policy.NotSelfTargetPolicy;
import uk.gov.justice.laa.datauserapi.application.command.policy.OpaLifecycleAuthorizationPolicy;
import uk.gov.justice.laa.datauserapi.application.command.policy.ProviderAdminFirmScopePolicy;
import uk.gov.justice.laa.datauserapi.application.command.policy.ProviderAdminOwnFirmPolicy;
import uk.gov.justice.laa.datauserapi.application.command.policy.ProviderAdminProfileFirmScopePolicy;
import uk.gov.justice.laa.datauserapi.application.command.policy.ReactivationHierarchyPolicy;
import uk.gov.justice.laa.datauserapi.application.command.service.UserCommandService;
import uk.gov.justice.laa.datauserapi.application.command.service.UserAccountLifecycleService;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.EntraUserCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.FirmCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.BulkDeactivateFirmUsersCommand;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.DeactivateUserCommand;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.DeleteUserCommand;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.ReactivateUserCommand;
import uk.gov.justice.laa.datauserapi.application.command.userprofile.RefreshSilasStatusCommand;
import uk.gov.justice.laa.datauserapi.application.command.userprofile.UpdateUserProfileOfficesCommand;
import uk.gov.justice.laa.datauserapi.contracts.domain.DeactivateUserReason;
import uk.gov.justice.laa.datauserapi.contracts.domain.DeleteUserReason;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.client.ts.TechServicesClient;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.entity.DeleteUserReasonEntity;
import uk.gov.justice.laa.datauserapi.model.AuthzRole;
import uk.gov.justice.laa.datauserapi.model.DeactivationType;
import uk.gov.justice.laa.datauserapi.model.Permission;
import uk.gov.justice.laa.datauserapi.exception.OpaUnavailableException;
import uk.gov.justice.laa.datauserapi.service.DeactivationTypeResolver;

import java.util.Optional;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.any;
import static org.mockito.ArgumentMatchers.eq;

/**
 * Verifies the advisor is wired into the Spring proxy chain and gates the real user account commands
 * ({@link DeleteUserCommand}, {@link DeactivateUserCommand}, {@link ReactivateUserCommand}), including the
 * global Provider Admin firm-scope policy.
 */
@SpringJUnitConfig(RbacMethodSecurityIntegrationTest.TestConfig.class)
class RbacMethodSecurityIntegrationTest {

    @ParameterizedTest
    @CsvSource({
        "true,false,false", "true,true,false", "false,false,false", "false,true,false",
        "true,false,true", "true,true,true", "false,false,true", "false,true,true"
    })
    void opaDenialAndOutageBlockHandlerWrites(boolean legacyEnabled, boolean reactivate, boolean outage) {
        new ApplicationContextRunner().withUserConfiguration(TestConfig.class)
                .withPropertyValues("app.authorization.legacy-policies-enabled=" + legacyEnabled,
                        "app.authorization.opa-enabled=true")
                .run(context -> {
                    UUID targetId = UUID.randomUUID();
                    AuthenticatedEntraUser actor = RbacTestActors.actorWith(
                            Permission.DISABLE_EXTERNAL_USER, Permission.ENABLE_EXTERNAL_USER);
                    UserCommandService users = context.getBean(UserCommandService.class);
                    when(users.isInternalUser(targetId)).thenReturn(false);
                    when(users.getDeactivationContext(targetId))
                            .thenReturn(new UserDeactivationContext(true, Optional.of(DeactivationType.NONE)));
                    OpaLifecycleAuthorizationPolicy opa = context.getBean(OpaLifecycleAuthorizationPolicy.class);
                    OpaUnavailableException failure = new OpaUnavailableException("OPA is unavailable");
                    if (outage) {
                        when(opa.evaluate(any(), eq(actor))).thenThrow(failure);
                    } else {
                        when(opa.evaluate(any(), eq(actor))).thenReturn(PolicyDecision.DENY);
                    }
                    PlatformTransactionManager transactions = context.getBean(PlatformTransactionManager.class);
                    clearInvocations(transactions);

                    Runnable invoke = reactivate
                            ? () -> context.getBean(ReactivateUserHandler.class)
                                    .handle(new ReactivateUserCommand(targetId, "verified"), actor)
                            : () -> context.getBean(DeactivateUserHandler.class)
                                    .handle(new DeactivateUserCommand(targetId, DeactivateUserReason.Absence), actor);
                    if (outage) {
                        assertThatThrownBy(invoke::run).isSameAs(failure);
                    } else {
                        assertThatThrownBy(invoke::run).isInstanceOf(AccessDeniedException.class);
                    }

                    verify(opa).evaluate(any(), eq(actor));
                    verify(transactions, never()).getTransaction(argThat(definition -> !definition.isReadOnly()));
                    verifyNoInteractions(context.getBean(UserAccountLifecycleService.class),
                            context.getBean(TechServicesClient.class),
                            context.getBean(EntraUserCommandRepository.class),
                            AopTestUtils.getUltimateTargetObject(context.getBean(ReactivateUserService.class)));
                    if (legacyEnabled) {
                        verify(users).isInternalUser(targetId);
                        if (reactivate) {
                            verify(users).getDeactivationContext(targetId);
                        }
                    } else {
                        verifyNoInteractions(users);
                    }
                });
    }

    @Configuration
    @EnableMethodSecurity(proxyTargetClass = true)
    @EnableTransactionManagement(proxyTargetClass = true)
    @Import({RbacMethodSecurityConfig.class, UpdateUserDetailsHandler.class,
        UpdateUserProfileOfficesHandler.class, UpdateUserProfileUnrestrictedOfficesHandler.class,
        ReactivateUserHandler.class, ConvertToMultiFirmHandler.class, InternalActorOnlyPolicy.class,
        BulkDeactivateFirmUsersMapper.class})
    static class TestConfig {
        @Bean
        PlatformTransactionManager transactionManager() {
            return mock(PlatformTransactionManager.class);
        }

        @Bean
        UserCommandService userCommandService() {
            return mock(UserCommandService.class);
        }

        @Bean
        FirmCommandRepository firmCommandRepository() {
            return mock(FirmCommandRepository.class);
        }

        @Bean
        EntraUserCommandRepository entraUserCommandRepository() {
            return mock(EntraUserCommandRepository.class);
        }

        @Bean
        UserAccountLifecycleService userAccountLifecycleService() {
            return mock(UserAccountLifecycleService.class);
        }

        @Bean
        DeactivationTypeResolver deactivationTypeResolver() {
            return mock(DeactivationTypeResolver.class);
        }

        @Bean
        TechServicesClient techServicesClient() {
            return mock(TechServicesClient.class);
        }

        @Bean
        UpdateUserDetailsService updateUserDetailsService() {
            return mock(UpdateUserDetailsService.class);
        }

        @Bean
        ReactivateUserService reactivateUserService() {
            return mock(ReactivateUserService.class);
        }

        @Bean
        UserProfileOfficeService userProfileOfficeService() {
            return mock(UserProfileOfficeService.class);
        }

        @Bean
        ConvertToMultiFirmService convertToMultiFirmService() {
            return mock(ConvertToMultiFirmService.class);
        }

        @Bean
        DeactivateUserService deactivateUserService(
                UserAccountLifecycleService lifecycleService,
                DeactivationTypeResolver deactivationTypeResolver,
                TechServicesClient techServicesClient) {
            return new DeactivateUserService(lifecycleService, deactivationTypeResolver, techServicesClient);
        }

        @Bean
        DeactivateUserHandler realDeactivateUserHandler(DeactivateUserService service) {
            return new DeactivateUserHandler(service);
        }

        @Bean
        BulkDeactivateFirmUsersService bulkDeactivateFirmUsersService(
                FirmCommandRepository firmCommandRepository,
                EntraUserCommandRepository entraUserCommandRepository,
                DeactivateUserService deactivateUserService) {
            return new BulkDeactivateFirmUsersService(
                    firmCommandRepository, entraUserCommandRepository, deactivateUserService);
        }

        @Bean
        BulkDeactivateFirmUsersHandler realBulkDeactivateFirmUsersHandler(
                BulkDeactivateFirmUsersService service, BulkDeactivateFirmUsersMapper mapper) {
            return new BulkDeactivateFirmUsersHandler(service, mapper);
        }

        @Bean
        ProviderAdminFirmScopePolicy providerAdminFirmScopePolicy(UserCommandService userCommandService) {
            return new ProviderAdminFirmScopePolicy(userCommandService);
        }

        @Bean
        NotSelfTargetPolicy notSelfTargetPolicy() {
            return new NotSelfTargetPolicy();
        }

        @Bean
        ExternalTargetOnlyPolicy externalTargetOnlyPolicy(UserCommandService userCommandService) {
            return new ExternalTargetOnlyPolicy(userCommandService);
        }

        @Bean
        ReactivationHierarchyPolicy reactivationHierarchyPolicy() {
            return new ReactivationHierarchyPolicy();
        }

        @Bean
        OpaLifecycleAuthorizationPolicy opaLifecycleAuthorizationPolicy() {
            OpaLifecycleAuthorizationPolicy policy = mock(OpaLifecycleAuthorizationPolicy.class);
            when(policy.feature()).thenReturn(AuthorizationPolicyFeature.OPA);
            when(policy.evaluate(any(), any())).thenReturn(PolicyDecision.allow());
            return policy;
        }

        @Bean
        ReactivationPolicyInputResolver reactivationPolicyInputResolver(UserCommandService userCommandService) {
            return new ReactivationPolicyInputResolver(userCommandService);
        }

        @Bean
        ProviderAdminOwnFirmPolicy providerAdminOwnFirmPolicy() {
            return new ProviderAdminOwnFirmPolicy();
        }

        @Bean
        ProviderAdminProfileFirmScopePolicy providerAdminProfileFirmScopePolicy(UserCommandService userCommandService) {
            return new ProviderAdminProfileFirmScopePolicy(userCommandService);
        }

        @Bean
        NotSelfProfileTargetPolicy notSelfProfileTargetPolicy(UserCommandService userCommandService) {
            return new NotSelfProfileTargetPolicy(userCommandService);
        }

        @Bean
        ExternalProfileTargetOnlyPolicy externalProfileTargetOnlyPolicy(UserCommandService userCommandService) {
            return new ExternalProfileTargetOnlyPolicy(userCommandService);
        }

        @Bean
        FakeBulkDeactivateHandler fakeBulkDeactivateHandler() {
            return new FakeBulkDeactivateHandler();
        }

        @Bean
        FakeRefreshHandler fakeRefreshHandler() {
            return new FakeRefreshHandler();
        }

        @Bean
        FakeUpdateOfficesHandler fakeUpdateOfficesHandler() {
            return new FakeUpdateOfficesHandler();
        }

        @Bean
        FakeDeleteUserHandler fakeDeleteUserHandler() {
            return new FakeDeleteUserHandler();
        }

        @Bean
        FakeDeactivateUserHandler fakeDeactivateUserHandler() {
            return new FakeDeactivateUserHandler();
        }

        @Bean
        FakeReactivateUserHandler fakeReactivateUserHandler() {
            return new FakeReactivateUserHandler();
        }
    }

    static class FakeDeactivateUserHandler implements CommandHandler<DeactivateUserCommand> {
        @Override
        public CommandResult handle(DeactivateUserCommand command, AuthenticatedEntraUser actor) {
            return CommandResult.success("deactivated");
        }
    }

    static class FakeBulkDeactivateHandler implements CommandHandler<BulkDeactivateFirmUsersCommand> {
        @Override
        public CommandResult handle(BulkDeactivateFirmUsersCommand command, AuthenticatedEntraUser actor) {
            return CommandResult.success("bulk");
        }
    }

    static class FakeRefreshHandler implements CommandHandler<RefreshSilasStatusCommand> {
        @Override
        public CommandResult handle(RefreshSilasStatusCommand command, AuthenticatedEntraUser actor) {
            return CommandResult.success("refresh");
        }
    }

    static class FakeUpdateOfficesHandler implements CommandHandler<UpdateUserProfileOfficesCommand> {
        @Override
        public CommandResult handle(UpdateUserProfileOfficesCommand command, AuthenticatedEntraUser actor) {
            return CommandResult.success("offices");
        }
    }

    static class FakeReactivateUserHandler implements CommandHandler<ReactivateUserCommand> {
        @Override
        public CommandResult handle(ReactivateUserCommand command, AuthenticatedEntraUser actor) {
            return CommandResult.success("reactivated");
        }
    }

    static class FakeDeleteUserHandler implements CommandHandler<DeleteUserCommand> {
        private final AtomicInteger invocations = new AtomicInteger();

        AtomicInteger invocations() {
            return invocations;
        }

        @Override
        @Transactional
        public CommandResult handle(DeleteUserCommand command, AuthenticatedEntraUser actor) {
            invocations.incrementAndGet();
            return CommandResult.success("deleted");
        }
    }

    @Autowired
    private FakeDeleteUserHandler handler;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private UserCommandService userCommandService;

    @Autowired
    private FirmCommandRepository firmCommandRepository;

    @Autowired
    private EntraUserCommandRepository entraUserCommandRepository;

    @Autowired
    private UserAccountLifecycleService lifecycleService;

    @Autowired
    private DeactivationTypeResolver deactivationTypeResolver;

    @Autowired
    private BulkDeactivateFirmUsersHandler realBulkHandler;

    @Autowired
    private DeactivateUserHandler realDeactivateUserHandler;

    @Autowired
    private ConvertToMultiFirmHandler convertToMultiFirmHandler;
    @Autowired
    private ConvertToMultiFirmService convertToMultiFirmService;

    @Autowired
    private ReactivateUserHandler realReactivateUserHandler;
    @Autowired
    private ReactivateUserService reactivateService;

    @Autowired
    private UpdateUserDetailsHandler updateDetailsHandler;
    @Autowired
    private UpdateUserProfileOfficesHandler updateOfficesHandler;
    @Autowired
    private UpdateUserProfileUnrestrictedOfficesHandler updateUnrestrictedHandler;
    @Autowired
    private UpdateUserDetailsService updateDetailsService;
    @Autowired
    private UserProfileOfficeService profileOfficeService;

    @Autowired
    private FakeDeactivateUserHandler deactivateHandler;

    @Autowired
    private FakeReactivateUserHandler reactivateHandler;

    @Autowired
    private FakeBulkDeactivateHandler bulkHandler;

    @Autowired
    private FakeRefreshHandler refreshHandler;

    @Autowired
    private FakeUpdateOfficesHandler officesHandler;

    private final DeleteUserCommand command = DeleteUserCommand.builder()
            .userEntraObjectId(UUID.randomUUID())
            .deleteReason(DeleteUserReason.values()[0])
            .build();

    @AfterEach
    void cleanUp() {
        handler.invocations().set(0);
        reset(transactionManager, userCommandService, firmCommandRepository, entraUserCommandRepository,
                lifecycleService, deactivationTypeResolver);
        reset(AopTestUtils.getUltimateTargetObject(updateDetailsService),
                AopTestUtils.getUltimateTargetObject(profileOfficeService),
                AopTestUtils.getUltimateTargetObject(reactivateService),
                AopTestUtils.getUltimateTargetObject(convertToMultiFirmService));
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @ValueSource(strings = {"editPermission", "convertPermission", "external", "inactive", "noProfile",
        "unknownType", "self", "internalTarget"})
    void conversionDeniesBeforeInvokingServiceOrOpeningWriteTransaction(String restriction) {
        authenticate();
        AuthenticatedEntraUser permitted = RbacTestActors.actorWith(
                Permission.EDIT_EXTERNAL_USER, Permission.CONVERT_USER_TO_MULTI_FIRM);
        AuthenticatedUserProfile profile = permitted.activeProfile();
        AuthenticatedEntraUser actor = AuthenticatedEntraUser.builder().entraOid(permitted.entraOid())
                .activeProfile(restriction.equals("noProfile") ? null : AuthenticatedUserProfile.builder()
                        .activeProfile(!restriction.equals("inactive"))
                        .userType(restriction.equals("unknownType") ? null
                                : restriction.equals("external") ? UserType.EXTERNAL : UserType.INTERNAL)
                        .appRoles(profile.appRoles()).build()).build();
        if (restriction.equals("editPermission")) {
            actor = RbacTestActors.actorWith(Permission.CONVERT_USER_TO_MULTI_FIRM);
        } else if (restriction.equals("convertPermission")) {
            actor = RbacTestActors.actorWith(Permission.EDIT_EXTERNAL_USER);
        }
        UUID targetId = restriction.equals("self") ? UUID.fromString(actor.entraOid()) : UUID.randomUUID();
        when(userCommandService.isInternalUser(targetId)).thenReturn(restriction.equals("internalTarget"));
        AuthenticatedEntraUser finalActor = actor;

        assertThatThrownBy(() -> convertToMultiFirmHandler.handle(
                new ConvertToMultiFirmCommand(targetId), finalActor)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(AopTestUtils.<ConvertToMultiFirmService>getUltimateTargetObject(convertToMultiFirmService));
        verify(transactionManager, never()).getTransaction(argThat(def -> def != null && !def.isReadOnly()));
    }

    @Test
    void conversionAllowsPermissionsAcrossRolesAndDelegatesAfterAuthorization() {
        authenticate();
        AuthenticatedAppRole editRole = RbacTestActors.actorWith(Permission.EDIT_EXTERNAL_USER)
                .activeProfile().appRoles().iterator().next();
        AuthenticatedAppRole convertRole = RbacTestActors.actorWith(Permission.CONVERT_USER_TO_MULTI_FIRM)
                .activeProfile().appRoles().iterator().next();
        AuthenticatedEntraUser actor = AuthenticatedEntraUser.builder().entraOid(UUID.randomUUID().toString())
                .activeProfile(AuthenticatedUserProfile.builder().activeProfile(true).userType(UserType.INTERNAL)
                        .appRoles(Set.of(editRole, convertRole)).build()).build();
        ConvertToMultiFirmCommand command = new ConvertToMultiFirmCommand(UUID.randomUUID());
        ConvertToMultiFirmService service = AopTestUtils.getUltimateTargetObject(convertToMultiFirmService);
        CommandResult expected = CommandResult.success("converted");
        when(service.convert(command, actor)).thenReturn(expected);

        assertThat(convertToMultiFirmHandler.handle(command, actor)).isSameAs(expected);
        verify(service).convert(command, actor);
    }

    @Test
    void allowsInternalActorWithDeleteExternalUserPermissionWithoutFirmLookups() {
        authenticate();
        CommandResult result = handler.handle(command, RbacTestActors.actorWith(Permission.DELETE_EXTERNAL_USER));

        assertThat(result.success()).isTrue();
        assertThat(handler.invocations()).hasValue(1);
        verify(userCommandService, never()).isMultiFirmUser(command.userEntraObjectId());
    }

    @Test
    void deniesActorWithoutDeleteExternalUserPermissionBeforeTransactionOrHandlerRuns() {
        authenticate();
        AuthenticatedEntraUser actor = RbacTestActors.actorWith(Permission.VIEW_EXTERNAL_USER);

        assertThatThrownBy(() -> handler.handle(command, actor)).isInstanceOf(AccessDeniedException.class);
        assertThat(handler.invocations()).hasValue(0);
        verifyNoInteractions(transactionManager);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void gatesCallsThroughGenericCommandHandlerInterface() {
        authenticate();
        CommandHandler generic = handler;
        AuthenticatedEntraUser actor = RbacTestActors.actorWith();

        assertThatThrownBy(() -> generic.handle(command, actor)).isInstanceOf(AccessDeniedException.class);
        assertThat(handler.invocations()).hasValue(0);
    }

    @Test
    void authorizationPointcutMatchesOnlyExplicitEntryPoints() throws Exception {
        RbacMethodSecurityConfig.RequiresPermissionPointcut pointcut =
                new RbacMethodSecurityConfig.RequiresPermissionPointcut();
        Method mapperMethod = DeleteUserMapper.class.getMethod(
                "toAuditEntity", EntraUser.class, DeleteUserReasonEntity.class, String.class);
        Method handlerMethod = FakeDeleteUserHandler.class.getMethod(
                "handle", DeleteUserCommand.class, AuthenticatedEntraUser.class);

        assertThat(pointcut.matches(mapperMethod, DeleteUserMapper.class)).isFalse();
        assertThat(pointcut.matches(handlerMethod, FakeDeleteUserHandler.class)).isTrue();
        Method serviceMethod = DeactivateUserService.class.getMethod(
                "deactivate", UUID.class, DeactivateUserReason.class, AuthenticatedEntraUser.class);
        assertThat(pointcut.matches(serviceMethod, DeactivateUserService.class)).isFalse();
    }

    @Test
    void allowsExternalActorInSameFirm() {
        authenticate();
        UUID firmId = UUID.randomUUID();
        when(userCommandService.isMultiFirmUser(command.userEntraObjectId())).thenReturn(false);
        when(userCommandService.findActiveFirmId(command.userEntraObjectId())).thenReturn(Optional.of(firmId));

        CommandResult result = handler.handle(command, RbacTestActors.externalActorWith(firmId, Permission.DELETE_EXTERNAL_USER));

        assertThat(result.success()).isTrue();
        assertThat(handler.invocations()).hasValue(1);
    }

    @Test
    void deniesExternalActorFromDifferentFirmBeforeTransactionOrHandlerRuns() {
        authenticate();
        when(userCommandService.isMultiFirmUser(command.userEntraObjectId())).thenReturn(false);
        when(userCommandService.findActiveFirmId(command.userEntraObjectId())).thenReturn(Optional.of(UUID.randomUUID()));
        AuthenticatedEntraUser actor = RbacTestActors.externalActorWith(UUID.randomUUID(), Permission.DELETE_EXTERNAL_USER);

        assertThatThrownBy(() -> handler.handle(command, actor)).isInstanceOf(AccessDeniedException.class);
        assertThat(handler.invocations()).hasValue(0);
        // policies may open read-only lookups; the handler's read-write transaction must never start
        verify(transactionManager, never()).getTransaction(argThat(def -> def != null && !def.isReadOnly()));
    }

    @Test
    void deniesExternalActorTargetingMultiFirmUser() {
        authenticate();
        UUID firmId = UUID.randomUUID();
        when(userCommandService.isMultiFirmUser(command.userEntraObjectId())).thenReturn(true);
        AuthenticatedEntraUser actor = RbacTestActors.externalActorWith(firmId, Permission.DELETE_EXTERNAL_USER);

        assertThatThrownBy(() -> handler.handle(command, actor)).isInstanceOf(AccessDeniedException.class);
        assertThat(handler.invocations()).hasValue(0);
    }

    @Test
    void deactivateRequiresDisableExternalUserPermission() {
        authenticate();
        AuthenticatedEntraUser withoutPermission = RbacTestActors.actorWith(Permission.ENABLE_EXTERNAL_USER);

        assertThatThrownBy(() -> deactivateHandler.handle(deactivateCommand(), withoutPermission))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(deactivateHandler.handle(deactivateCommand(), RbacTestActors.actorWith(Permission.DISABLE_EXTERNAL_USER))
                .success()).isTrue();
    }

    @Test
    void deactivateDeniesSelfAndInternalTargetsButAllowsRepeatDeactivation() {
        authenticate();
        AuthenticatedEntraUser actor = RbacTestActors.actorWith(Permission.DISABLE_EXTERNAL_USER);
        DeactivateUserCommand self = DeactivateUserCommand.builder()
                .userEntraObjectId(UUID.fromString(actor.entraOid()))
                .deactivateReason(DeactivateUserReason.values()[0])
                .build();
        assertThatThrownBy(() -> deactivateHandler.handle(self, actor)).isInstanceOf(AccessDeniedException.class);

        when(userCommandService.isInternalUser(accountTargetId)).thenReturn(true);
        assertThatThrownBy(() -> deactivateHandler.handle(deactivateCommand(), actor)).isInstanceOf(AccessDeniedException.class);

        when(userCommandService.isInternalUser(accountTargetId)).thenReturn(false);
        when(userCommandService.isDeactivated(accountTargetId)).thenReturn(true);
        assertThat(deactivateHandler.handle(deactivateCommand(), actor).success()).isTrue();
    }

    @Test
    void reactivateRequiresEnableExternalUserPermission() {
        authenticate();
        when(userCommandService.getDeactivationContext(accountTargetId))
                .thenReturn(new UserDeactivationContext(true, Optional.of(DeactivationType.NONE)));

        assertThatThrownBy(() -> reactivateHandler.handle(reactivateCommand(),
                RbacTestActors.actorWith(Permission.DISABLE_EXTERNAL_USER))).isInstanceOf(AccessDeniedException.class);
        assertThat(reactivateHandler.handle(reactivateCommand(),
                RbacTestActors.actorWith(Permission.ENABLE_EXTERNAL_USER)).success()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"permission", "self", "internal", "firm", "hierarchy"})
    void realReactivationHandlerDeniesBeforeInvokingExtractedService(String restriction) {
        authenticate();
        AuthenticatedEntraUser actor = restriction.equals("permission") ? RbacTestActors.actorWith()
                : restriction.equals("firm") ? RbacTestActors.externalActorWith(UUID.randomUUID(), Permission.ENABLE_EXTERNAL_USER)
                : RbacTestActors.actorWith(Permission.ENABLE_EXTERNAL_USER);
        UUID targetId = restriction.equals("self") ? UUID.fromString(actor.entraOid()) : UUID.randomUUID();
        when(userCommandService.isInternalUser(targetId)).thenReturn(restriction.equals("internal"));
        when(userCommandService.findActiveFirmId(targetId)).thenReturn(Optional.of(UUID.randomUUID()));
        when(userCommandService.getDeactivationContext(targetId)).thenReturn(
                new UserDeactivationContext(true, Optional.of(restriction.equals("hierarchy")
                        ? DeactivationType.PRIVILEGED : DeactivationType.NONE)));

        assertThatThrownBy(() -> realReactivateUserHandler.handle(new ReactivateUserCommand(targetId, "verified"), actor))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(AopTestUtils.<ReactivateUserService>getUltimateTargetObject(reactivateService));
        verify(transactionManager, never()).getTransaction(argThat(def -> def != null && !def.isReadOnly()));
    }

    @Test
    void realReactivationHandlerDelegatesAfterAuthorization() {
        authenticate();
        AuthenticatedEntraUser actor = RbacTestActors.actorWith(Permission.ENABLE_EXTERNAL_USER);
        ReactivateUserCommand command = reactivateCommand();
        when(userCommandService.getDeactivationContext(accountTargetId))
                .thenReturn(new UserDeactivationContext(true, Optional.of(DeactivationType.NONE)));
        ReactivateUserService service = AopTestUtils.getUltimateTargetObject(reactivateService);
        CommandResult expected = CommandResult.success("reactivated");
        when(service.reactivate(command, actor)).thenReturn(expected);

        assertThat(realReactivateUserHandler.handle(command, actor)).isSameAs(expected);
        verify(service).reactivate(command, actor);
    }

    @Test
    void reactivateDeniesTargetThatIsNotDeactivated() {
        authenticate();
        AuthenticatedEntraUser actor = RbacTestActors.actorWith(Permission.ENABLE_EXTERNAL_USER);
        when(userCommandService.getDeactivationContext(accountTargetId))
                .thenReturn(new UserDeactivationContext(false, Optional.empty()));

        assertThatThrownBy(() -> reactivateHandler.handle(reactivateCommand(), actor)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void reactivateAppliesDeactivationHierarchy() {
        authenticate();
        when(userCommandService.getDeactivationContext(accountTargetId))
                .thenReturn(new UserDeactivationContext(true, Optional.of(DeactivationType.PRIVILEGED)));
        AuthenticatedEntraUser eua = RbacTestActors.actorWithRole(AuthzRole.EXTERNAL_USER_ADMIN.getRoleName(),
                Permission.ENABLE_EXTERNAL_USER);
        AuthenticatedEntraUser globalAdmin = RbacTestActors.actorWithRole(AuthzRole.GLOBAL_ADMIN.getRoleName(),
                Permission.ENABLE_EXTERNAL_USER);

        assertThatThrownBy(() -> reactivateHandler.handle(reactivateCommand(), eua)).isInstanceOf(AccessDeniedException.class);
        assertThat(reactivateHandler.handle(reactivateCommand(), globalAdmin).success()).isTrue();
    }

    @Test
    void deactivateAndReactivateAllowExternalActorInSameFirm() {
        authenticate();
        UUID firmId = UUID.randomUUID();
        when(userCommandService.isMultiFirmUser(accountTargetId)).thenReturn(false);
        when(userCommandService.findActiveFirmId(accountTargetId)).thenReturn(Optional.of(firmId));
        when(userCommandService.getDeactivationContext(accountTargetId))
                .thenReturn(new UserDeactivationContext(true, Optional.of(DeactivationType.FIRM)));
        AuthenticatedEntraUser actor = RbacTestActors.externalActorWithRole(firmId,
                AuthzRole.FIRM_USER_MANAGER.getRoleName(),
                Permission.DISABLE_EXTERNAL_USER, Permission.ENABLE_EXTERNAL_USER);

        assertThat(deactivateHandler.handle(deactivateCommand(), actor).success()).isTrue();
        assertThat(reactivateHandler.handle(reactivateCommand(), actor).success()).isTrue();
    }

    @Test
    void deactivateAndReactivateDenyExternalActorFromDifferentFirm() {
        authenticate();
        when(userCommandService.isMultiFirmUser(accountTargetId)).thenReturn(false);
        when(userCommandService.findActiveFirmId(accountTargetId)).thenReturn(Optional.of(UUID.randomUUID()));
        when(userCommandService.getDeactivationContext(accountTargetId))
                .thenReturn(new UserDeactivationContext(true, Optional.of(DeactivationType.NONE)));
        AuthenticatedEntraUser actor = RbacTestActors.externalActorWith(UUID.randomUUID(),
                Permission.DISABLE_EXTERNAL_USER, Permission.ENABLE_EXTERNAL_USER);

        assertThatThrownBy(() -> deactivateHandler.handle(deactivateCommand(), actor)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> reactivateHandler.handle(reactivateCommand(), actor)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void deactivateAndReactivateDenyExternalActorTargetingMultiFirmUser() {
        authenticate();
        when(userCommandService.isMultiFirmUser(accountTargetId)).thenReturn(true);
        UUID firmId = UUID.randomUUID();
        when(userCommandService.findActiveFirmId(accountTargetId)).thenReturn(Optional.of(firmId));
        when(userCommandService.getDeactivationContext(accountTargetId))
                .thenReturn(new UserDeactivationContext(true, Optional.of(DeactivationType.NONE)));
        AuthenticatedEntraUser actor = RbacTestActors.externalActorWithRole(firmId,
                AuthzRole.FIRM_USER_MANAGER.getRoleName(),
                Permission.DISABLE_EXTERNAL_USER, Permission.ENABLE_EXTERNAL_USER);

        assertThatThrownBy(() -> deactivateHandler.handle(deactivateCommand(), actor)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> reactivateHandler.handle(reactivateCommand(), actor)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void deleteDeniesSelfAndInternalTargets() {
        authenticate();
        AuthenticatedEntraUser actor = RbacTestActors.actorWith(Permission.DELETE_EXTERNAL_USER);
        DeleteUserCommand self = DeleteUserCommand.builder()
                .userEntraObjectId(UUID.fromString(actor.entraOid()))
                .deleteReason(DeleteUserReason.values()[0])
                .build();
        assertThatThrownBy(() -> handler.handle(self, actor)).isInstanceOf(AccessDeniedException.class);

        when(userCommandService.isInternalUser(command.userEntraObjectId())).thenReturn(true);
        assertThatThrownBy(() -> handler.handle(command, actor)).isInstanceOf(AccessDeniedException.class);
        assertThat(handler.invocations()).hasValue(0);
    }

    // Concrete ids (not any()): UserCommandService is wrapped in a transactional proxy whose
    // transaction-manager mock call would otherwise consume the Mockito matcher.
    private final UUID accountTargetId = UUID.randomUUID();

    private DeactivateUserCommand deactivateCommand() {
        return DeactivateUserCommand.builder()
                .userEntraObjectId(accountTargetId)
                .deactivateReason(DeactivateUserReason.values()[0])
                .build();
    }

    private ReactivateUserCommand reactivateCommand() {
        return ReactivateUserCommand.builder().userEntraObjectId(accountTargetId).build();
    }

    private static void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("user", null, "SCOPE_user_data.admin"));
    }

    @ParameterizedTest
    @CsvSource({
        "details,permission", "details,self", "details,internal", "details,firm",
        "offices,permission", "offices,self", "offices,internal", "offices,firm",
        "unrestricted,permission", "unrestricted,self", "unrestricted,internal", "unrestricted,firm"
    })
    void extractedUpdateHandlersDenyBeforeCallingServices(String operation, String restriction) {
        authenticate();
        UUID targetId = UUID.randomUUID();
        Permission permission = operation.equals("details") ? Permission.EDIT_USER_DETAILS : Permission.EDIT_USER_OFFICE;
        AuthenticatedEntraUser actor = restriction.equals("permission") ? RbacTestActors.actorWith()
                : restriction.equals("firm") ? RbacTestActors.externalActorWith(UUID.randomUUID(), permission)
                : RbacTestActors.actorWith(permission);
        if (restriction.equals("self") && operation.equals("details")) {
            targetId = UUID.fromString(actor.entraOid());
        }
        when(userCommandService.isInternalUser(targetId)).thenReturn(restriction.equals("internal"));
        when(userCommandService.findProfileUserType(targetId))
                .thenReturn(Optional.of(restriction.equals("internal") ? UserType.INTERNAL : UserType.EXTERNAL));
        when(userCommandService.findProfileEntraOid(targetId))
                .thenReturn(Optional.of(restriction.equals("self") ? actor.entraOid() : UUID.randomUUID().toString()));
        when(userCommandService.findActiveFirmId(targetId)).thenReturn(Optional.of(UUID.randomUUID()));
        when(userCommandService.findProfileFirmId(targetId)).thenReturn(Optional.of(UUID.randomUUID()));
        final UUID target = targetId;

        assertThatThrownBy(() -> invokeUpdateHandler(operation, target, actor))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(AopTestUtils.getUltimateTargetObject(updateDetailsService),
                AopTestUtils.getUltimateTargetObject(profileOfficeService));
        verify(transactionManager, never()).getTransaction(argThat(def -> def != null && !def.isReadOnly()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"details", "offices", "unrestricted"})
    void extractedUpdateHandlersDelegateForAuthorizedActors(String operation) {
        authenticate();
        UUID targetId = UUID.randomUUID();
        Permission permission = operation.equals("details") ? Permission.EDIT_USER_DETAILS : Permission.EDIT_USER_OFFICE;
        AuthenticatedEntraUser actor = RbacTestActors.actorWith(permission);
        UpdateUserDetailsService details = AopTestUtils.getUltimateTargetObject(updateDetailsService);
        UserProfileOfficeService offices = AopTestUtils.getUltimateTargetObject(profileOfficeService);
        CommandResult expected = CommandResult.success("updated");
        when(details.update(new UpdateUserDetailsCommand(targetId, "New", null, null), actor)).thenReturn(expected);
        when(offices.updateOffices(new UpdateUserProfileOfficesCommand(targetId, Set.of()), actor)).thenReturn(expected);
        when(offices.updateUnrestrictedAccess(new UpdateUserProfileUnrestrictedOfficesCommand(targetId, true), actor))
                .thenReturn(expected);

        assertThat(invokeUpdateHandler(operation, targetId, actor)).isSameAs(expected);
    }

    private CommandResult invokeUpdateHandler(String operation, UUID targetId, AuthenticatedEntraUser actor) {
        return switch (operation) {
            case "details" -> updateDetailsHandler.handle(new UpdateUserDetailsCommand(targetId, "New", null, null), actor);
            case "offices" -> updateOfficesHandler.handle(new UpdateUserProfileOfficesCommand(targetId, Set.of()), actor);
            case "unrestricted" -> updateUnrestrictedHandler.handle(
                    new UpdateUserProfileUnrestrictedOfficesCommand(targetId, true), actor);
            default -> throw new IllegalArgumentException("Unknown update operation: " + operation);
        };
    }

    @Test
    void bulkDeactivateRequiresBulkDisablePermission() {
        authenticate();
        BulkDeactivateFirmUsersCommand bulk = new BulkDeactivateFirmUsersCommand("FIRM1", DeactivateUserReason.values()[0]);

        assertThatThrownBy(() -> bulkHandler.handle(bulk, RbacTestActors.actorWith(Permission.DISABLE_EXTERNAL_USER)))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(bulkHandler.handle(bulk, RbacTestActors.actorWith(Permission.BULK_DISABLE_FIRM_USERS)).success())
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void realBulkHandlerRequiresOnlyBulkPermissionForActiveAndDisabledUsers(boolean active) {
        authenticate();
        UUID userId = UUID.randomUUID();
        BulkDeactivateFirmUsersCommand bulk = new BulkDeactivateFirmUsersCommand(
                "FIRM1", DeactivateUserReason.values()[0]);
        when(firmCommandRepository.existsByCode("FIRM1")).thenReturn(true);
        when(entraUserCommandRepository.findUserEntraOidsByFirmCode("FIRM1"))
                .thenReturn(List.of(userId.toString()));

        final AuthenticatedEntraUser actor = RbacTestActors.actorWith(Permission.BULK_DISABLE_FIRM_USERS);
        assertThatThrownBy(() -> realBulkHandler.handle(
                bulk, RbacTestActors.actorWith(Permission.DISABLE_EXTERNAL_USER)))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(lifecycleService, firmCommandRepository, entraUserCommandRepository);
        EntraUserDto userDto = EntraUserDto.builder().id(userId.toString()).active(active).build();
        UserAccountLifecycleService lifecycleMock = AopTestUtils.getUltimateTargetObject(lifecycleService);
        when(deactivationTypeResolver.resolve(actor)).thenReturn(DeactivationType.NONE);
        when(lifecycleMock.deactivate(
                eq(userId), eq(actor.entraOid()), eq(bulk.deactivateReason()), any()))
                .thenAnswer(invocation -> {
                    java.util.function.Function<EntraUserDto, DeactivationType> beforeUpdate = invocation.getArgument(3);
                    assertThat(beforeUpdate.apply(userDto)).isEqualTo(DeactivationType.NONE);
                    return CommandResult.success("deactivated");
                });

        CommandResult bulkOnlyResult = realBulkHandler.handle(bulk, actor);

        assertThat(bulkOnlyResult.success()).isTrue();
        verify(lifecycleMock).deactivate(eq(userId), eq(actor.entraOid()), eq(bulk.deactivateReason()), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"bulkOnly", "self", "internal", "multiFirm", "differentFirm"})
    void individualHandlerRetainsItsPermissionAndTargetPolicies(String restriction) {
        authenticate();
        AuthenticatedEntraUser actor = restriction.equals("bulkOnly")
                ? RbacTestActors.actorWith(Permission.BULK_DISABLE_FIRM_USERS)
                : RbacTestActors.actorWith(Permission.DISABLE_EXTERNAL_USER);
        if (restriction.equals("multiFirm") || restriction.equals("differentFirm")) {
            actor = RbacTestActors.externalActorWith(UUID.randomUUID(), Permission.DISABLE_EXTERNAL_USER);
        }
        UUID targetId = restriction.equals("self") ? UUID.fromString(actor.entraOid()) : UUID.randomUUID();
        when(userCommandService.isInternalUser(targetId)).thenReturn(restriction.equals("internal"));
        when(userCommandService.isMultiFirmUser(targetId)).thenReturn(restriction.equals("multiFirm"));
        when(userCommandService.findActiveFirmId(targetId)).thenReturn(Optional.of(UUID.randomUUID()));
        final AuthenticatedEntraUser finalActor = actor;

        assertThatThrownBy(() -> realDeactivateUserHandler.handle(
                new DeactivateUserCommand(targetId, DeactivateUserReason.Absence), finalActor))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(lifecycleService);
        verify(transactionManager, never()).getTransaction(argThat(def -> def != null && !def.isReadOnly()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"self", "internal", "multiFirm", "differentFirm"})
    void realBulkWorkflowDoesNotApplyIndividualTargetPolicies(String targetKind) {
        authenticate();
        UUID firmId = UUID.randomUUID();
        AuthenticatedEntraUser actor = RbacTestActors.actorWith(Permission.BULK_DISABLE_FIRM_USERS);
        if (targetKind.equals("multiFirm") || targetKind.equals("differentFirm")) {
            actor = AuthenticatedEntraUser.builder().entraOid(actor.entraOid())
                    .activeProfile(AuthenticatedUserProfile.builder()
                            .userType(UserType.EXTERNAL).firmId(firmId).firmCode("FIRM1")
                            .appRoles(actor.activeProfile().appRoles()).build())
                    .build();
        }
        UUID userId = targetKind.equals("self") ? UUID.fromString(actor.entraOid()) : UUID.randomUUID();
        when(firmCommandRepository.existsByCode("FIRM1")).thenReturn(true);
        when(entraUserCommandRepository.findUserEntraOidsByFirmCode("FIRM1")).thenReturn(List.of(userId.toString()));
        when(userCommandService.isInternalUser(userId)).thenReturn(targetKind.equals("internal"));
        when(userCommandService.isMultiFirmUser(userId)).thenReturn(targetKind.equals("multiFirm"));
        when(userCommandService.findActiveFirmId(userId)).thenReturn(Optional.of(UUID.randomUUID()));
        UserAccountLifecycleService lifecycleMock = AopTestUtils.getUltimateTargetObject(lifecycleService);
        when(deactivationTypeResolver.resolve(actor)).thenReturn(DeactivationType.NONE);
        when(lifecycleMock.deactivate(eq(userId), eq(actor.entraOid()), eq(DeactivateUserReason.Absence), any()))
                .thenAnswer(invocation -> {
                    java.util.function.Function<EntraUserDto, DeactivationType> beforeUpdate = invocation.getArgument(3);
                    assertThat(beforeUpdate.apply(EntraUserDto.builder().id(userId.toString()).build()))
                            .isEqualTo(DeactivationType.NONE);
                    return CommandResult.success("deactivated");
                });

        CommandResult result = realBulkHandler.handle(
                new BulkDeactivateFirmUsersCommand("FIRM1", DeactivateUserReason.Absence), actor);

        assertThat(result.success()).isTrue();
        assertThat(result.message()).contains("Deactivated 1 users");
        verifyNoInteractions(userCommandService);
        verify(lifecycleMock).deactivate(eq(userId), eq(actor.entraOid()), eq(DeactivateUserReason.Absence), any());
    }

    @Test
    void bulkDeactivateIsRestrictedToOwnFirmForExternalActors() {
        authenticate();
        BulkDeactivateFirmUsersCommand bulk = new BulkDeactivateFirmUsersCommand("FIRM1", DeactivateUserReason.values()[0]);
        AuthenticatedEntraUser external = RbacTestActors.externalActorWith(UUID.randomUUID(),
                Permission.BULK_DISABLE_FIRM_USERS);

        assertThatThrownBy(() -> bulkHandler.handle(bulk, external)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> realBulkHandler.handle(bulk, external)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(firmCommandRepository, entraUserCommandRepository, lifecycleService);
    }

    @Test
    void refreshSilasStatusRequiresItsPermission() {
        authenticate();

        assertThatThrownBy(() -> refreshHandler.handle(new RefreshSilasStatusCommand(), RbacTestActors.actorWith()))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(refreshHandler.handle(new RefreshSilasStatusCommand(),
                RbacTestActors.actorWith(Permission.UPDATE_USER_PROFILE_SILAS_STATUS)).success()).isTrue();
    }

    @Test
    void updateOfficesRequiresPermissionAndScopesExternalActorsToTheirFirm() {
        authenticate();
        UUID profileId = UUID.randomUUID();
        UUID firmId = UUID.randomUUID();
        UpdateUserProfileOfficesCommand offices = new UpdateUserProfileOfficesCommand(profileId, Set.of());

        assertThatThrownBy(() -> officesHandler.handle(offices, RbacTestActors.actorWith()))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(officesHandler.handle(offices, RbacTestActors.actorWith(Permission.EDIT_USER_OFFICE)).success())
                .isTrue();

        AuthenticatedEntraUser external = RbacTestActors.externalActorWith(firmId, Permission.EDIT_USER_OFFICE);
        when(userCommandService.findProfileFirmId(profileId)).thenReturn(Optional.of(UUID.randomUUID()));
        assertThatThrownBy(() -> officesHandler.handle(offices, external)).isInstanceOf(AccessDeniedException.class);

        when(userCommandService.findProfileFirmId(profileId)).thenReturn(Optional.of(firmId));
        assertThat(officesHandler.handle(offices, external).success()).isTrue();
    }

    @Test
    void updateOfficesRejectsOwnAccountAndInternalTargetProfiles() {
        authenticate();
        UUID profileId = UUID.randomUUID();
        UpdateUserProfileOfficesCommand offices = new UpdateUserProfileOfficesCommand(profileId, Set.of());
        AuthenticatedEntraUser actor = RbacTestActors.actorWith(Permission.EDIT_USER_OFFICE);

        when(userCommandService.findProfileEntraOid(profileId)).thenReturn(Optional.of(actor.entraOid()));
        assertThatThrownBy(() -> officesHandler.handle(offices, actor)).isInstanceOf(AccessDeniedException.class);

        when(userCommandService.findProfileEntraOid(profileId)).thenReturn(Optional.of("someone-else"));
        when(userCommandService.findProfileUserType(profileId)).thenReturn(Optional.of(UserType.INTERNAL));
        assertThatThrownBy(() -> officesHandler.handle(offices, actor)).isInstanceOf(AccessDeniedException.class);
    }
}

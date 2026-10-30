package uk.gov.justice.laa.datauserapi.security.rbac;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.command.handler.BulkDeactivateFirmUsersHandler;
import uk.gov.justice.laa.datauserapi.application.command.handler.CommandHandler;
import uk.gov.justice.laa.datauserapi.application.command.handler.DeactivateUserHandler;
import uk.gov.justice.laa.datauserapi.application.command.mapper.DeleteUserMapper;
import uk.gov.justice.laa.datauserapi.application.command.policy.ExternalProfileTargetOnlyPolicy;
import uk.gov.justice.laa.datauserapi.application.command.policy.ExternalTargetOnlyPolicy;
import uk.gov.justice.laa.datauserapi.application.command.policy.NotSelfProfileTargetPolicy;
import uk.gov.justice.laa.datauserapi.application.command.policy.NotSelfTargetPolicy;
import uk.gov.justice.laa.datauserapi.application.command.policy.ProviderAdminFirmScopePolicy;
import uk.gov.justice.laa.datauserapi.application.command.policy.ProviderAdminOwnFirmPolicy;
import uk.gov.justice.laa.datauserapi.application.command.policy.ProviderAdminProfileFirmScopePolicy;
import uk.gov.justice.laa.datauserapi.application.command.policy.ReactivationHierarchyPolicy;
import uk.gov.justice.laa.datauserapi.application.command.policy.TargetNotDeactivatedPolicy;
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
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.model.AuthzRole;
import uk.gov.justice.laa.datauserapi.model.DeactivationType;
import uk.gov.justice.laa.datauserapi.model.Permission;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Verifies the advisor is wired into the Spring proxy chain and gates the real user account commands
 * ({@link DeleteUserCommand}, {@link DeactivateUserCommand}, {@link ReactivateUserCommand}), including the
 * global Provider Admin firm-scope policy.
 */
@SpringJUnitConfig(RbacMethodSecurityIntegrationTest.TestConfig.class)
class RbacMethodSecurityIntegrationTest {

    @Configuration
    @EnableMethodSecurity(proxyTargetClass = true)
    @EnableTransactionManagement(proxyTargetClass = true)
    @Import(RbacMethodSecurityConfig.class)
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
        DeactivateUserHandler realDeactivateUserHandler(
                UserAccountLifecycleService lifecycleService,
                DeactivationTypeResolver deactivationTypeResolver,
                TechServicesClient techServicesClient) {
            return new DeactivateUserHandler(lifecycleService, deactivationTypeResolver, techServicesClient);
        }

        @Bean
        BulkDeactivateFirmUsersHandler realBulkDeactivateFirmUsersHandler(
                FirmCommandRepository firmCommandRepository,
                EntraUserCommandRepository entraUserCommandRepository,
                DeactivateUserHandler deactivateUserHandler) {
            return new BulkDeactivateFirmUsersHandler(
                    firmCommandRepository, entraUserCommandRepository, deactivateUserHandler);
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
        TargetNotDeactivatedPolicy targetNotDeactivatedPolicy(UserCommandService userCommandService) {
            return new TargetNotDeactivatedPolicy(userCommandService);
        }

        @Bean
        ReactivationHierarchyPolicy reactivationHierarchyPolicy(UserCommandService userCommandService) {
            return new ReactivationHierarchyPolicy(userCommandService);
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
        SecurityContextHolder.clearContext();
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
    void authorizationPointcutOnlyMatchesCommandHandlerEntryPoints() throws Exception {
        RbacMethodSecurityConfig.RequiresPermissionPointcut pointcut =
                new RbacMethodSecurityConfig.RequiresPermissionPointcut();
        Method mapperMethod = DeleteUserMapper.class.getMethod(
                "toAuditEntity", EntraUser.class, DeleteUserCommand.class, String.class);
        Method handlerMethod = FakeDeleteUserHandler.class.getMethod(
                "handle", DeleteUserCommand.class, AuthenticatedEntraUser.class);

        assertThat(pointcut.matches(mapperMethod, DeleteUserMapper.class)).isFalse();
        assertThat(pointcut.matches(handlerMethod, FakeDeleteUserHandler.class)).isTrue();
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
        when(userCommandService.isDeactivated(accountTargetId)).thenReturn(true);
        when(userCommandService.findDeactivationType(accountTargetId)).thenReturn(Optional.of(DeactivationType.NONE));

        assertThatThrownBy(() -> reactivateHandler.handle(reactivateCommand(),
                RbacTestActors.actorWith(Permission.DISABLE_EXTERNAL_USER))).isInstanceOf(AccessDeniedException.class);
        assertThat(reactivateHandler.handle(reactivateCommand(),
                RbacTestActors.actorWith(Permission.ENABLE_EXTERNAL_USER)).success()).isTrue();
    }

    @Test
    void reactivateDeniesTargetThatIsNotDeactivated() {
        authenticate();
        AuthenticatedEntraUser actor = RbacTestActors.actorWith(Permission.ENABLE_EXTERNAL_USER);

        assertThatThrownBy(() -> reactivateHandler.handle(reactivateCommand(), actor)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void reactivateAppliesDeactivationHierarchy() {
        authenticate();
        when(userCommandService.isDeactivated(accountTargetId)).thenReturn(true);
        when(userCommandService.findDeactivationType(accountTargetId)).thenReturn(Optional.of(DeactivationType.PRIVILEGED));
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
        when(userCommandService.isDeactivated(accountTargetId)).thenReturn(true);
        when(userCommandService.findDeactivationType(accountTargetId)).thenReturn(Optional.of(DeactivationType.FIRM));
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
        when(userCommandService.isDeactivated(accountTargetId)).thenReturn(true);
        when(userCommandService.findDeactivationType(accountTargetId)).thenReturn(Optional.of(DeactivationType.NONE));
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
        when(userCommandService.isDeactivated(accountTargetId)).thenReturn(true);
        when(userCommandService.findDeactivationType(accountTargetId)).thenReturn(Optional.of(DeactivationType.NONE));
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
    void realBulkHandlerRequiresBothBulkAndPerUserPermissionsAndCountsDeniedUsers(boolean active) {
        authenticate();
        UUID userId = UUID.randomUUID();
        BulkDeactivateFirmUsersCommand bulk = new BulkDeactivateFirmUsersCommand(
                "FIRM1", DeactivateUserReason.values()[0]);
        when(firmCommandRepository.existsByCode("FIRM1")).thenReturn(true);
        when(entraUserCommandRepository.findUsersByFirmCode("FIRM1"))
                .thenReturn(List.of(EntraUser.builder()
                        .id(UUID.randomUUID()).entraOid(userId.toString()).active(active).build()));

        CommandResult bulkOnlyResult = realBulkHandler.handle(
                bulk, RbacTestActors.actorWith(Permission.BULK_DISABLE_FIRM_USERS));
        assertThat(bulkOnlyResult.success()).isFalse();
        assertThat(bulkOnlyResult.message()).contains("failed to deactivate 1 users");

        AuthenticatedEntraUser actor = RbacTestActors.actorWith(
                Permission.BULK_DISABLE_FIRM_USERS, Permission.DISABLE_EXTERNAL_USER);
        EntraUserDto userDto = EntraUserDto.builder().id(userId.toString()).build();
        when(lifecycleService.loadEntraUserDtoForDeactivation(userId)).thenReturn(userDto);
        when(deactivationTypeResolver.resolve(actor)).thenReturn(DeactivationType.NONE);
        when(lifecycleService.deactivate(
                userId, actor.entraOid(), DeactivationType.NONE, bulk.deactivateReason()))
                .thenReturn(CommandResult.success("deactivated"));

        CommandResult bothPermissionsResult = realBulkHandler.handle(bulk, actor);

        assertThat(bothPermissionsResult.success()).isTrue();
        verify(lifecycleService).loadEntraUserDtoForDeactivation(userId);
        verify(lifecycleService).deactivate(userId, actor.entraOid(), DeactivationType.NONE, bulk.deactivateReason());
    }

    @Test
    void bulkDeactivateIsRestrictedToOwnFirmForExternalActors() {
        authenticate();
        BulkDeactivateFirmUsersCommand bulk = new BulkDeactivateFirmUsersCommand("FIRM1", DeactivateUserReason.values()[0]);
        AuthenticatedEntraUser external = RbacTestActors.externalActorWith(UUID.randomUUID(),
                Permission.BULK_DISABLE_FIRM_USERS);

        assertThatThrownBy(() -> bulkHandler.handle(bulk, external)).isInstanceOf(AccessDeniedException.class);
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

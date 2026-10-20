package uk.gov.justice.laa.datauserapi.security.rbac;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
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
import uk.gov.justice.laa.datauserapi.application.command.handler.CommandHandler;
import uk.gov.justice.laa.datauserapi.application.command.policy.ProviderAdminFirmScopePolicy;
import uk.gov.justice.laa.datauserapi.application.command.service.UserCommandService;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.DeactivateUserCommand;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.DeleteUserCommand;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.ReactivateUserCommand;
import uk.gov.justice.laa.datauserapi.contracts.domain.DeactivateUserReason;
import uk.gov.justice.laa.datauserapi.contracts.domain.DeleteUserReason;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.model.Permission;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

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
        ProviderAdminFirmScopePolicy providerAdminFirmScopePolicy(UserCommandService userCommandService) {
            return new ProviderAdminFirmScopePolicy(userCommandService);
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
    private FakeDeactivateUserHandler deactivateHandler;

    @Autowired
    private FakeReactivateUserHandler reactivateHandler;

    private final DeleteUserCommand command = DeleteUserCommand.builder()
            .userEntraObjectId(UUID.randomUUID())
            .deleteReason(DeleteUserReason.values()[0])
            .build();

    @AfterEach
    void cleanUp() {
        handler.invocations().set(0);
        reset(transactionManager, userCommandService);
        SecurityContextHolder.clearContext();
    }

    @Test
    void allowsInternalActorWithDeleteExternalUserPermissionWithoutFirmLookups() {
        authenticate();
        CommandResult result = handler.handle(command, RbacTestActors.actorWith(Permission.DELETE_EXTERNAL_USER));

        assertThat(result.success()).isTrue();
        assertThat(handler.invocations()).hasValue(1);
        verifyNoInteractions(userCommandService);
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
    void deactivateAndReactivateAllowInternalActorWithoutAnyPermissionOrFirmLookups() {
        authenticate();
        AuthenticatedEntraUser internal = RbacTestActors.actorWith();

        assertThat(deactivateHandler.handle(deactivateCommand(), internal).success()).isTrue();
        assertThat(reactivateHandler.handle(reactivateCommand(), internal).success()).isTrue();
        verifyNoInteractions(userCommandService);
    }

    @Test
    void deactivateAndReactivateAllowExternalActorInSameFirmWithoutAnyPermission() {
        authenticate();
        UUID firmId = UUID.randomUUID();
        when(userCommandService.isMultiFirmUser(accountTargetId)).thenReturn(false);
        when(userCommandService.findActiveFirmId(accountTargetId)).thenReturn(Optional.of(firmId));
        AuthenticatedEntraUser actor = RbacTestActors.externalActorWith(firmId);

        assertThat(deactivateHandler.handle(deactivateCommand(), actor).success()).isTrue();
        assertThat(reactivateHandler.handle(reactivateCommand(), actor).success()).isTrue();
    }

    @Test
    void deactivateAndReactivateDenyExternalActorFromDifferentFirm() {
        authenticate();
        when(userCommandService.isMultiFirmUser(accountTargetId)).thenReturn(false);
        when(userCommandService.findActiveFirmId(accountTargetId)).thenReturn(Optional.of(UUID.randomUUID()));
        AuthenticatedEntraUser actor = RbacTestActors.externalActorWith(UUID.randomUUID());

        assertThatThrownBy(() -> deactivateHandler.handle(deactivateCommand(), actor)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> reactivateHandler.handle(reactivateCommand(), actor)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void deactivateAndReactivateDenyExternalActorTargetingMultiFirmUser() {
        authenticate();
        when(userCommandService.isMultiFirmUser(accountTargetId)).thenReturn(true);
        AuthenticatedEntraUser actor = RbacTestActors.externalActorWith(UUID.randomUUID());

        assertThatThrownBy(() -> deactivateHandler.handle(deactivateCommand(), actor)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> reactivateHandler.handle(reactivateCommand(), actor)).isInstanceOf(AccessDeniedException.class);
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
}

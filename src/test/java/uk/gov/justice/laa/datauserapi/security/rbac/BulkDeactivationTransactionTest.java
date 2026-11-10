package uk.gov.justice.laa.datauserapi.security.rbac;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import uk.gov.justice.laa.datauserapi.application.command.service.BulkDeactivateFirmUsersService;
import uk.gov.justice.laa.datauserapi.application.command.service.DeactivateUserService;
import uk.gov.justice.laa.datauserapi.application.command.service.UserAccountLifecycleService;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.EntraUserCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.FirmCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.BulkDeactivateFirmUsersCommand;
import uk.gov.justice.laa.datauserapi.client.ts.TechServicesClient;
import uk.gov.justice.laa.datauserapi.contracts.domain.DeactivateUserReason;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.exception.TechServicesClientException;
import uk.gov.justice.laa.datauserapi.model.DeactivationType;
import uk.gov.justice.laa.datauserapi.model.Permission;
import uk.gov.justice.laa.datauserapi.service.DeactivationTypeResolver;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(BulkDeactivationTransactionTest.Config.class)
class BulkDeactivationTransactionTest {

    @Configuration
    @EnableMethodSecurity(proxyTargetClass = true)
    @EnableTransactionManagement(proxyTargetClass = true)
    @Import({RbacMethodSecurityConfig.class, BulkDeactivateFirmUsersService.class,
        DeactivateUserService.class})
    static class Config {
        @Bean
        CountingTransactionManager transactionManager() {
            return new CountingTransactionManager();
        }

        @Bean
        FirmCommandRepository firmRepository() {
            return mock(FirmCommandRepository.class);
        }

        @Bean
        EntraUserCommandRepository userRepository() {
            return mock(EntraUserCommandRepository.class);
        }

        @Bean
        UserAccountLifecycleService lifecycleService() {
            return mock(UserAccountLifecycleService.class);
        }

        @Bean
        TechServicesClient techServicesClient() {
            return mock(TechServicesClient.class);
        }

        @Bean
        DeactivationTypeResolver typeResolver() {
            return mock(DeactivationTypeResolver.class);
        }

    }

    static class CountingTransactionManager extends AbstractPlatformTransactionManager {
        int begun;
        int committed;
        int rolledBack;

        @Override
        protected Object doGetTransaction() {
            return TransactionSynchronizationManager.isActualTransactionActive();
        }

        @Override
        protected boolean isExistingTransaction(Object transaction) {
            return (Boolean) transaction;
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            begun++;
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            committed++;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rolledBack++;
        }
    }

    @Autowired
    private BulkDeactivateFirmUsersService bulkService;
    @Autowired
    private FirmCommandRepository firmRepository;
    @Autowired
    private EntraUserCommandRepository userRepository;
    @Autowired
    private UserAccountLifecycleService lifecycleService;
    @Autowired
    private TechServicesClient techServicesClient;
    @Autowired
    private DeactivationTypeResolver typeResolver;
    @Autowired
    private CountingTransactionManager transactions;

    @Test
    void rollsBackFailedUserAndCommitsNextUserIndependently() throws Exception {
        UUID failedId = UUID.randomUUID();
        UUID successfulId = UUID.randomUUID();
        AuthenticatedEntraUser actor = RbacTestActors.actorWith(Permission.BULK_DISABLE_FIRM_USERS);
        UserAccountLifecycleService lifecycleMock = AopTestUtils.getTargetObject(lifecycleService);
        when(firmRepository.existsByCode("FIRM1")).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return true;
        });
        when(userRepository.findUserEntraOidsByFirmCode("FIRM1"))
                .thenReturn(List.of(failedId.toString(), successfulId.toString()));
        when(lifecycleMock.deactivate(any(), eq(actor.entraOid()), eq(DeactivateUserReason.Absence), any()))
                .thenAnswer(invocation -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                    UUID id = invocation.getArgument(0);
                    Function<EntraUserDto, DeactivationType> beforeUpdate = invocation.getArgument(3);
                    assertThat(beforeUpdate.apply(EntraUserDto.builder().id(id.toString()).build()))
                            .isEqualTo(DeactivationType.NONE);
                    return CommandResult.success("done");
                });
        doAnswer(invocation -> {
            EntraUserDto user = invocation.getArgument(0);
            if (user.getId().equals(failedId.toString())) {
                throw new TechServicesClientException("timeout");
            }
            return null;
        }).when(techServicesClient).deactivateUser(any(), any());
        when(typeResolver.resolve(actor)).thenReturn(DeactivationType.NONE);

        var result = bulkService.deactivate(
                new BulkDeactivateFirmUsersCommand("FIRM1", DeactivateUserReason.Absence), actor);

        assertThat(result.deactivated()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(transactions.begun).isEqualTo(2);
        assertThat(transactions.rolledBack).isEqualTo(1);
        assertThat(transactions.committed).isEqualTo(1);
        verify(lifecycleMock).deactivate(eq(successfulId), eq(actor.entraOid()), eq(DeactivateUserReason.Absence), any());
    }
}

package uk.gov.justice.laa.datauserapi.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import uk.gov.justice.laa.datauserapi.application.command.ccms.TriggerCcmsRoleSyncCommand;
import uk.gov.justice.laa.datauserapi.application.command.handler.TriggerCcmsRoleSyncHandler;
import uk.gov.justice.laa.datauserapi.application.command.service.CcmsRoleSyncProfileService;
import uk.gov.justice.laa.datauserapi.application.command.service.CcmsRoleSyncService;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.UserProfileCommandRepository;
import uk.gov.justice.laa.datauserapi.config.CcmsRoleSyncConfig;
import uk.gov.justice.laa.datauserapi.config.SilasStatusRefreshConfig;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(CcmsRoleSyncAsyncIntegrationTest.Config.class)
@ExtendWith(OutputCaptureExtension.class)
class CcmsRoleSyncAsyncIntegrationTest {
    @Autowired
    private TriggerCcmsRoleSyncHandler handler;
    @Autowired
    private UserProfileCommandRepository repository;
    @Autowired
    @Qualifier("ccmsRoleSyncExecutor")
    private ThreadPoolTaskExecutor executor;

    private final AuthenticatedEntraUser actor = AuthenticatedEntraUser.builder().entraOid("actor").build();

    @BeforeEach
    void resetRepository() {
        reset(repository);
    }

    @Test
    void returnsImmediatelyRejectsBusyWorkerAndAllowsRetrigger() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<String> thread = new AtomicReference<>();
        when(repository.findUserProfileIdsForCcmsSync()).thenAnswer(invocation -> {
            thread.set(Thread.currentThread().getName());
            started.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Worker was not released");
            }
            return List.of();
        });
        long completed = executor.getThreadPoolExecutor().getCompletedTaskCount();
        try {
            assertTimeoutPreemptively(Duration.ofSeconds(2),
                    () -> assertThat(handler.handle(new TriggerCcmsRoleSyncCommand(), actor).success()).isTrue());
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(thread.get()).startsWith("ccms-role-sync-");
            assertThat(handler.handle(new TriggerCcmsRoleSyncCommand(), actor).success()).isFalse();
        } finally {
            release.countDown();
            awaitCompleted(completed + 1);
        }
        assertThat(handler.handle(new TriggerCcmsRoleSyncCommand(), actor).success()).isTrue();
        awaitCompleted(completed + 2);
    }

    @Test
    void logsAsyncFailureWithActorAndAllowsRetry(CapturedOutput output) {
        when(repository.findUserProfileIdsForCcmsSync()).thenThrow(new IllegalStateException("Deliberate CCMS failure"));
        long completed = executor.getThreadPoolExecutor().getCompletedTaskCount();
        assertThat(handler.handle(new TriggerCcmsRoleSyncCommand(), actor).success()).isTrue();
        awaitCompleted(completed + 1);
        assertThat(output.getOut()).contains("Asynchronous command failed", "Deliberate CCMS failure", "sync");
        doReturn(List.of()).when(repository).findUserProfileIdsForCcmsSync();
        assertThat(handler.handle(new TriggerCcmsRoleSyncCommand(), actor).success()).isTrue();
        awaitCompleted(completed + 2);
    }

    private void awaitCompleted(long expected) {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            while (executor.getThreadPoolExecutor().getCompletedTaskCount() < expected) {
                Thread.sleep(10);
            }
        });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({SilasStatusRefreshConfig.class, CcmsRoleSyncConfig.class,
            CcmsRoleSyncService.class, TriggerCcmsRoleSyncHandler.class})
    static class Config {
        @Bean
        UserProfileCommandRepository repository() {
            return mock(UserProfileCommandRepository.class);
        }

        @Bean
        CcmsRoleSyncProfileService profiles() {
            return mock(CcmsRoleSyncProfileService.class);
        }
    }
}

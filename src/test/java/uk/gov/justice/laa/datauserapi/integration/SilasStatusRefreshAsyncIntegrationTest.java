package uk.gov.justice.laa.datauserapi.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import uk.gov.justice.laa.datauserapi.application.command.handler.RefreshSilasStatusHandler;
import uk.gov.justice.laa.datauserapi.application.command.service.SilasStatusRefreshBatchService;
import uk.gov.justice.laa.datauserapi.application.command.service.SilasStatusRefreshService;
import uk.gov.justice.laa.datauserapi.application.command.userprofile.RefreshSilasStatusCommand;
import uk.gov.justice.laa.datauserapi.config.SilasStatusRefreshConfig;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

@SpringJUnitConfig(SilasStatusRefreshAsyncIntegrationTest.Config.class)
@ExtendWith(OutputCaptureExtension.class)
class SilasStatusRefreshAsyncIntegrationTest {

    @Autowired
    private RefreshSilasStatusHandler handler;
    @Autowired
    private SilasStatusRefreshBatchService batchService;
    @Autowired
    private ThreadPoolTaskExecutor executor;

    @BeforeEach
    void resetBatch() {
        reset(batchService);
    }

    @Test
    void dispatchesWithoutBlockingRequestAndRejectsOverlapUntilWorkerCompletes() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        final AuthenticatedEntraUser actor1 = AuthenticatedEntraUser.builder().id(UUID.randomUUID()).entraOid("first").firstName("first").lastName("last").build();
        final AuthenticatedEntraUser actor2 = AuthenticatedEntraUser.builder().id(UUID.randomUUID()).entraOid("second").firstName("second").lastName("last").build();
        final AuthenticatedEntraUser actor3 = AuthenticatedEntraUser.builder().id(UUID.randomUUID()).entraOid("third").firstName("third").lastName("last").build();
        AtomicReference<String> workerThread = new AtomicReference<>();
        doAnswer(invocation -> {
            workerThread.set(Thread.currentThread().getName());
            started.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Test did not release worker");
            }
            return new SilasStatusRefreshBatchService.BatchResult(null, 0, 0);
        }).when(batchService).refreshBatch(null, "first");
        long completed = executor.getThreadPoolExecutor().getCompletedTaskCount();

        try {
            assertTimeoutPreemptively(Duration.ofSeconds(2),
                    () -> assertThat(handler.handle(new RefreshSilasStatusCommand(), actor1).success()).isTrue());
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(workerThread.get()).startsWith("silas-status-refresh-");
            assertThat(handler.handle(new RefreshSilasStatusCommand(), actor2).success()).isFalse();
        } finally {
            release.countDown();
            awaitCompletedTasks(completed + 1);
        }
        when(batchService.refreshBatch(null, "third"))
                .thenReturn(new SilasStatusRefreshBatchService.BatchResult(null, 0, 0));
        assertThat(handler.handle(new RefreshSilasStatusCommand(), actor3).success()).isTrue();
        awaitCompletedTasks(completed + 2);
        verify(batchService).refreshBatch(null, "third");
    }

    @Test
    void logsAsyncFailureStopsTraversalAndAllowsAnotherRun(CapturedOutput output) throws Exception {
        UUID cursor = UUID.randomUUID();
        when(batchService.refreshBatch(null, "failed-actor"))
                .thenReturn(new SilasStatusRefreshBatchService.BatchResult(cursor, 2, 3));
        when(batchService.refreshBatch(cursor, "failed-actor"))
                .thenThrow(new IllegalStateException("Deliberate background failure"));
        long completed = executor.getThreadPoolExecutor().getCompletedTaskCount();

        final AuthenticatedEntraUser failedActor = AuthenticatedEntraUser.builder().id(UUID.randomUUID()).entraOid("failed-actor").firstName("failed").lastName("actor").build();
        assertThat(handler.handle(new RefreshSilasStatusCommand(), failedActor).success()).isTrue();
        awaitCompletedTasks(completed + 1);

        assertThat(output.getOut()).contains("Asynchronous SiLAS status refresh failed for actor failed-actor",
                "Previously committed batches remain updated.", "Deliberate background failure");
        when(batchService.refreshBatch(isNull(), any(String.class)))
                .thenReturn(new SilasStatusRefreshBatchService.BatchResult(null, 0, 0));
        final AuthenticatedEntraUser retryActor = AuthenticatedEntraUser.builder().id(UUID.randomUUID()).entraOid("retry-actor").firstName("retry").lastName("actor").build();
        assertThat(handler.handle(new RefreshSilasStatusCommand(), retryActor).success()).isTrue();
        awaitCompletedTasks(completed + 2);
        verify(batchService).refreshBatch(null, "retry-actor");
    }

    private void awaitCompletedTasks(long expected) {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            while (executor.getThreadPoolExecutor().getCompletedTaskCount() < expected) {
                Thread.sleep(10);
            }
        });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({SilasStatusRefreshConfig.class, SilasStatusRefreshService.class, RefreshSilasStatusHandler.class})
    static class Config {
        @Bean
        SilasStatusRefreshBatchService batchService() {
            return mock(SilasStatusRefreshBatchService.class);
        }
    }
}

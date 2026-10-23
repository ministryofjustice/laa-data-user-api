package uk.gov.justice.laa.datauserapi.config;

import org.junit.jupiter.api.Test;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Method;
import java.util.concurrent.ThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;

class SilasStatusRefreshConfigTest {
    private final SilasStatusRefreshConfig config = new SilasStatusRefreshConfig();

    @Test
    void silasStatusRefreshExecutor_hasSingleWorkerNoQueueAndNamedThreads() {
        ThreadPoolTaskExecutor executor = config.silasStatusRefreshExecutor();

        assertThat(executor.getCorePoolSize()).isEqualTo(1);
        assertThat(executor.getMaxPoolSize()).isEqualTo(1);
        assertThat(executor.getQueueCapacity()).isZero();
        assertThat(executor.getThreadNamePrefix()).isEqualTo("silas-status-refresh-");
        executor.initialize();
        try {
            assertThat(executor.getThreadPoolExecutor().getRejectedExecutionHandler())
                    .isInstanceOf(ThreadPoolExecutor.AbortPolicy.class);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void uncaughtExceptionHandler_acceptsWorkerFailureAndActorContext() throws NoSuchMethodException {
        AsyncUncaughtExceptionHandler handler = config.getAsyncUncaughtExceptionHandler();
        Method method = SilasStatusRefreshConfigTest.class.getDeclaredMethod("workerMethod", String.class);

        handler.handleUncaughtException(new IllegalStateException("batch failed"), method, new Object[]{"actor-oid"});
    }

    private void workerMethod(String actorOid) {
    }
}

package uk.gov.justice.laa.datauserapi.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import uk.gov.justice.laa.datauserapi.exception.CcmsSyncException;

import java.time.ZoneOffset;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CcmsRoleSyncConfigTest {
    private final CcmsRoleSyncConfig config = new CcmsRoleSyncConfig();

    @Test
    void providesUtcClock() {
        assertThat(config.ccmsRoleSyncClock().getZone()).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    void retriesOnlyExpectedSyncFailuresThreeTimes() {
        var attempts = new AtomicInteger();
        var failure = new CcmsSyncException(CcmsSyncException.Reason.DOWNSTREAM_TIMEOUT);
        var retry = config.ccmsRoleSyncRetry();
        assertThatThrownBy(() -> retry.execute(context -> {
            attempts.incrementAndGet();
            throw failure;
        })).isSameAs(failure);
        assertThat(attempts).hasValue(3);

        attempts.set(0);
        var unexpected = new IllegalStateException("unexpected");
        assertThatThrownBy(() -> retry.execute(context -> {
            attempts.incrementAndGet();
            throw unexpected;
        })).isSameAs(unexpected);
        assertThat(attempts).hasValue(1);
    }

    @Test
    void ccmsRoleSyncExecutor_hasSingleWorkerNoQueueAndNamedThreads() {
        ThreadPoolTaskExecutor executor = config.ccmsRoleSyncExecutor();

        assertThat(executor.getCorePoolSize()).isEqualTo(1);
        assertThat(executor.getMaxPoolSize()).isEqualTo(1);
        assertThat(executor.getQueueCapacity()).isZero();
        assertThat(executor.getThreadNamePrefix()).isEqualTo("ccms-role-sync-");
        executor.initialize();
        try {
            assertThat(executor.getThreadPoolExecutor().getRejectedExecutionHandler())
                    .isInstanceOf(ThreadPoolExecutor.AbortPolicy.class);
        } finally {
            executor.shutdown();
        }
    }
}

package uk.gov.justice.laa.datauserapi.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;

class CcmsRoleSyncConfigTest {
    private final CcmsRoleSyncConfig config = new CcmsRoleSyncConfig();

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

package uk.gov.justice.laa.datauserapi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.RetryOperations;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import uk.gov.justice.laa.datauserapi.exception.CcmsSyncException;

import java.time.Clock;

@Configuration
public class CcmsRoleSyncConfig {

    @Bean("ccmsRoleSyncClock")
    public Clock ccmsRoleSyncClock() {
        return Clock.systemUTC();
    }

    @Bean("ccmsRoleSyncRetry")
    public RetryOperations ccmsRoleSyncRetry() {
        return RetryTemplate.builder().maxAttempts(3).fixedBackoff(100)
                .retryOn(CcmsSyncException.class).build();
    }

    @Bean
    public ThreadPoolTaskExecutor ccmsRoleSyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("ccms-role-sync-");
        return executor;
    }
}

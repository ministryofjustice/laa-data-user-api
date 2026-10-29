package uk.gov.justice.laa.datauserapi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class CcmsRoleSyncConfig {

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

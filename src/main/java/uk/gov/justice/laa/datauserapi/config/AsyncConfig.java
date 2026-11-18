package uk.gov.justice.laa.datauserapi.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

/** Enables {@code @Async} processing; executors are defined by the configuration owning each workload. */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean
    public SimpleAsyncTaskExecutor taskExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("application-async-");
        executor.setTaskDecorator(new MdcTaskDecorator());
        return executor;
    }
}

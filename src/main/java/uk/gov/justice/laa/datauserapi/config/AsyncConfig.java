package uk.gov.justice.laa.datauserapi.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Enables {@code @Async} processing; executors are defined by the configuration owning each workload. */
@Configuration
@EnableAsync
public class AsyncConfig {
}

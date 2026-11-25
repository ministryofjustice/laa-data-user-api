package uk.gov.justice.laa.datauserapi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Supplies the UTC clock used to timestamp audit CSV export filenames; kept as an injectable
 * bean (never {@code LocalDateTime.now()}/{@code Instant.now()} directly) so tests can control it.
 */
@Configuration
public class AuditExportConfig {

    @Bean("auditExportClock")
    public Clock auditExportClock() {
        return Clock.systemUTC();
    }
}

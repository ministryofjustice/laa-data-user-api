package uk.gov.justice.laa.datauserapi.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.logstash.logback.encoder.LogstashEncoder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.logback.LogbackLoggingSystem;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static net.logstash.logback.argument.StructuredArguments.kv;

class LoggingProfileTest {

    private final LogbackLoggingSystem logging = new LogbackLoggingSystem(getClass().getClassLoader());

    @AfterEach
    void restoreJsonLogging() {
        configure("test");
    }

    @ParameterizedTest
    @ValueSource(strings = {"local", "dev", "test", "prod", "staging"})
    void allProfilesRetainJsonCorrelationStructuredArgumentsAndStackTrace(String profile) throws Exception {
        configure(profile);
        ConsoleAppender<ILoggingEvent> appender = console();
        assertThat(appender.getEncoder()).isInstanceOf(LogstashEncoder.class);

        var json = new ObjectMapper().readTree(appender.getEncoder().encode(event()));

        assertThat(json.path("correlationId").asText()).isEqualTo("upstream-123");
        assertThat(json.path("message").asText()).isEqualTo("Operation failed");
        assertThat(json.path("exception_group").asText()).isEqualTo("invalid_request");
        assertThat(json.path("exception_reason").asText()).isEqualTo("invalid_firm_sort");
        assertThat(json.path("requestUri").asText()).isEqualTo("/api/v1/queries/firms");
        assertThat(json.path("stack_trace").asText()).contains("IllegalStateException: diagnostic");
    }

    private void configure(String profile) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.setActiveProfiles(profile);
        logging.cleanUp();
        logging.beforeInitialize();
        logging.initialize(new LoggingInitializationContext(environment), "classpath:logback-spring.xml", null);
    }

    private ConsoleAppender<ILoggingEvent> console() {
        Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        return (ConsoleAppender<ILoggingEvent>) root.getAppender("CONSOLE");
    }

    private LoggingEvent event() {
        Logger logger = (Logger) LoggerFactory.getLogger(LoggingProfileTest.class);
        LoggingEvent event = new LoggingEvent(getClass().getName(), logger, ch.qos.logback.classic.Level.WARN,
                "Operation failed", new IllegalStateException("diagnostic"),
                new Object[]{kv("exception_group", "invalid_request"),
                    kv("exception_reason", "invalid_firm_sort"), kv("requestUri", "/api/v1/queries/firms")});
        event.setMDCPropertyMap(Map.of("correlationId", "upstream-123"));
        return event;
    }
}

package uk.gov.justice.laa.datauserapi.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
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

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingProfileTest {

    private final LogbackLoggingSystem logging = new LogbackLoggingSystem(getClass().getClassLoader());

    @AfterEach
    void restoreJsonLogging() {
        configure("test");
    }

    @ParameterizedTest
    @ValueSource(strings = {"local"})
    void developmentProfilesUseReadableTextWithCorrelationIdAndStackTrace(String profile) {
        configure(profile);
        ConsoleAppender<ILoggingEvent> appender = console();
        assertThat(appender.getEncoder()).isInstanceOf(PatternLayoutEncoder.class);

        String output = new String(appender.getEncoder().encode(event()), StandardCharsets.UTF_8);

        assertThat(output).contains("[correlationId=upstream-123]", "Operation failed",
                "IllegalStateException: diagnostic");
        assertThat(output).doesNotStartWith("{");
    }

    @ParameterizedTest
    @ValueSource(strings = {"dev", "test", "prod", "staging"})
    void otherProfilesUseJsonWithCorrelationIdAndStackTrace(String profile) throws Exception {
        configure(profile);
        ConsoleAppender<ILoggingEvent> appender = console();
        assertThat(appender.getEncoder()).isInstanceOf(LogstashEncoder.class);

        var json = new ObjectMapper().readTree(appender.getEncoder().encode(event()));

        assertThat(json.path("correlationId").asText()).isEqualTo("upstream-123");
        assertThat(json.path("message").asText()).isEqualTo("Operation failed");
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
                "Operation failed", new IllegalStateException("diagnostic"), null);
        event.setMDCPropertyMap(Map.of("correlationId", "upstream-123"));
        return event;
    }
}

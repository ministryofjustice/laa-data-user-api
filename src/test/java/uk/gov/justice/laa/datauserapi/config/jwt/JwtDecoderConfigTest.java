package uk.gov.justice.laa.datauserapi.config.jwt;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import uk.gov.justice.laa.datauserapi.exception.JwtConfigurationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtDecoderConfigTest {

    @Test
    void invalidConfigurationLogsStructuredExceptionFields() {
        JwtDecoderConfig config = new JwtDecoderConfig();
        JwtProperties properties = new JwtProperties("https://issuer.example", "", "test-audience");
        Logger logger = (Logger) LoggerFactory.getLogger(JwtDecoderConfig.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertThatThrownBy(() -> config.jwtDecoder(properties)).isInstanceOf(JwtConfigurationException.class);
            assertThat(appender.list.stream().filter(event -> event.getThrowableProxy() != null).toList())
                    .singleElement().satisfies(event -> assertThat(event.getArgumentArray()).extracting(Object::toString)
                            .contains("exception_group=illegal_argument", "exception_reason=illegal_argument"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(JwtDecoderConfig.class)
            .withPropertyValues(
                    "spring.profiles.active=prod",
                    "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://localhost/issuer",
                    "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://localhost/keys",
                    "spring.security.oauth2.resourceserver.jwt.audience=test-audience");

    @Test
    void createsProductionDecoder() {
        contextRunner.run(context ->
                assertThat(context).hasNotFailed().hasSingleBean(JwtDecoder.class));
    }

    @Test
    void propagatesDecoderConstructionFailure() {
        contextRunner.withPropertyValues("spring.security.oauth2.resourceserver.jwt.jwk-set-uri=")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class);
                });
    }
}

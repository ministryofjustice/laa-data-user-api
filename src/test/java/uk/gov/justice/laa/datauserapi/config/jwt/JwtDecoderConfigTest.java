package uk.gov.justice.laa.datauserapi.config.jwt;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.justice.laa.datauserapi.exception.JwtConfigurationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtDecoderConfigTest {

    @Test
    void invalidConfigurationLogsStructuredExceptionFields() {
        JwtDecoderConfig config = new JwtDecoderConfig();
        ReflectionTestUtils.setField(config, "issuerUri", "https://issuer.example");
        ReflectionTestUtils.setField(config, "jwkSetUri", "");
        ReflectionTestUtils.setField(config, "audience", "test-audience");
        Logger logger = (Logger) LoggerFactory.getLogger(JwtDecoderConfig.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertThatThrownBy(config::jwtDecoder).isInstanceOf(JwtConfigurationException.class);
            assertThat(appender.list.stream().filter(event -> event.getThrowableProxy() != null).toList())
                    .singleElement().satisfies(event -> assertThat(event.getArgumentArray()).extracting(Object::toString)
                            .contains("exception_group=illegal_argument", "exception_reason=illegal_argument"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}

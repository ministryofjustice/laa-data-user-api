package uk.gov.justice.laa.datauserapi.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.logstash.logback.encoder.LogstashEncoder;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static net.logstash.logback.argument.StructuredArguments.kv;

class CorrelationLoggingIntegrationTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void realAsyncProxiesPropagateRequestContextOnEveryExecutorAndDoNotLeakIt() throws Exception {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
                AsyncConfig.class, BulkDeactivationConfig.class, SilasStatusRefreshConfig.class, AsyncWorker.class)) {
            AsyncWorker worker = context.getBean(AsyncWorker.class);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockHttpServletRequest incoming = new MockHttpServletRequest();
            incoming.addHeader(CorrelationIdFilter.HEADER_NAME, "upstream-request-123");
            new CorrelationIdFilter().doFilter(incoming, response, (request, res) -> {
                try {
                    String id = response.getHeader(CorrelationIdFilter.HEADER_NAME);
                    assertThat(id).isEqualTo("upstream-request-123");
                    assertThat(worker.defaultContext().get(5, TimeUnit.SECONDS)).containsEntry("correlationId", id);
                    assertThat(worker.bulkContext().get(5, TimeUnit.SECONDS)).containsEntry("correlationId", id);
                    assertThat(worker.refreshContext().get(5, TimeUnit.SECONDS)).containsEntry("correlationId", id);
                } catch (Exception ex) {
                    throw new jakarta.servlet.ServletException(ex);
                }
            });

            assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
            assertThat(worker.defaultContext().get(5, TimeUnit.SECONDS)).isEmpty();
            assertThat(worker.bulkContext().get(5, TimeUnit.SECONDS)).isEmpty();
            assertThat(worker.refreshContext().get(5, TimeUnit.SECONDS)).isEmpty();
        }
    }

    @Test
    void encoderAddsCorrelationIdWithoutChangingLogMessageAndPreservesStructuredFieldsAndStackTrace() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(CorrelationLoggingIntegrationTest.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>() {
            @Override
            protected void append(ILoggingEvent event) {
                event.prepareForDeferredProcessing();
                super.append(event);
            }
        };
        appender.start();
        logger.addAppender(appender);
        LogstashEncoder encoder = new LogstashEncoder();
        encoder.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        encoder.start();
        try {
            new CorrelationIdFilter().doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                    (request, response) -> logger.warn("Operation failed", kv("exception_reason", "invalid_request"),
                            new IllegalStateException("diagnostic")));
            ILoggingEvent event = appender.list.getFirst();
            JsonNode json = new ObjectMapper().readTree(new String(encoder.encode(event), StandardCharsets.UTF_8));

            assertThat(json.path("correlationId").asText()).isNotBlank();
            assertThat(json.path("message").asText()).isEqualTo("Operation failed");
            assertThat(json.path("exception_reason").asText()).isEqualTo("invalid_request");
            assertThat(json.path("stack_trace").asText()).contains("IllegalStateException: diagnostic");
        } finally {
            encoder.stop();
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    static class AsyncWorker {
        @Async
        public CompletableFuture<Map<String, String>> defaultContext() {
            return capturedContext();
        }

        @Async("bulkDeactivationExecutor")
        public CompletableFuture<Map<String, String>> bulkContext() {
            return capturedContext();
        }

        @Async("silasStatusRefreshExecutor")
        public CompletableFuture<Map<String, String>> refreshContext() {
            return capturedContext();
        }

        private CompletableFuture<Map<String, String>> capturedContext() {
            Map<String, String> captured = MDC.getCopyOfContextMap();
            MDC.put("workerOnly", "must-not-leak");
            return CompletableFuture.completedFuture(captured == null ? Map.of() : captured);
        }
    }
}

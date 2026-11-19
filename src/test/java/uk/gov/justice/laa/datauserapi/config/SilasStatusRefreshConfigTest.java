package uk.gov.justice.laa.datauserapi.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Method;
import java.util.concurrent.ThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;

class SilasStatusRefreshConfigTest {
    private final SilasStatusRefreshConfig config = new SilasStatusRefreshConfig();

    @Test
    void silasStatusRefreshExecutor_hasSingleWorkerNoQueueAndNamedThreads() {
        ThreadPoolTaskExecutor executor = config.silasStatusRefreshExecutor();

        assertThat(executor.getCorePoolSize()).isEqualTo(1);
        assertThat(executor.getMaxPoolSize()).isEqualTo(1);
        assertThat(executor.getQueueCapacity()).isZero();
        assertThat(executor.getThreadNamePrefix()).isEqualTo("silas-status-refresh-");
        executor.initialize();
        try {
            assertThat(executor.getThreadPoolExecutor().getRejectedExecutionHandler())
                    .isInstanceOf(ThreadPoolExecutor.AbortPolicy.class);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void uncaughtExceptionHandler_acceptsWorkerFailureAndActorContext() throws NoSuchMethodException {
        AsyncUncaughtExceptionHandler handler = config.getAsyncUncaughtExceptionHandler();
        Method method = SilasStatusRefreshConfigTest.class.getDeclaredMethod("workerMethod", String.class);
        Logger logger = (Logger) LoggerFactory.getLogger(SilasStatusRefreshConfig.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            handler.handleUncaughtException(new IllegalStateException("batch failed"), method, new Object[]{"actor-oid"});

            assertThat(appender.list).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                assertThat(event.getArgumentArray()).extracting(Object::toString)
                        .contains("exception_group=illegal_state", "exception_reason=illegal_state",
                                "actorEntraOid=actor-oid", "method=workerMethod");
                assertThat(event.getThrowableProxy().getClassName()).isEqualTo(IllegalStateException.class.getName());
            });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private void workerMethod(String actorOid) {
    }
}

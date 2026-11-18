package uk.gov.justice.laa.datauserapi.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MdcTaskDecoratorTest {

    private final MdcTaskDecorator decorator = new MdcTaskDecorator();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void capturesContextAtSubmissionAndRestoresWorkerContext() {
        MDC.put("correlationId", "submitted");
        MDC.put("other", "context");
        Runnable task = decorator.decorate(() -> {
            assertThat(MDC.getCopyOfContextMap()).containsExactlyInAnyOrderEntriesOf(
                    Map.of("correlationId", "submitted", "other", "context"));
            MDC.put("taskOnly", "value");
        });
        MDC.setContextMap(Map.of("correlationId", "worker"));

        task.run();

        assertThat(MDC.getCopyOfContextMap()).containsExactlyEntriesOf(Map.of("correlationId", "worker"));
    }

    @Test
    void restoresContextEvenWhenTaskFails() {
        MDC.put("correlationId", "submitted");
        IllegalStateException failure = new IllegalStateException("task failed");
        Runnable task = decorator.decorate(() -> {
            assertThat(MDC.get("correlationId")).isEqualTo("submitted");
            throw failure;
        });
        MDC.clear();

        assertThatThrownBy(task::run).isSameAs(failure);

        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void taskWithoutContextDoesNotInheritWorkerContext() {
        Runnable task = decorator.decorate(() -> assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty());
        MDC.put("correlationId", "worker");

        task.run();

        assertThat(MDC.get("correlationId")).isEqualTo("worker");
    }
}

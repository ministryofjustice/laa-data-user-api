package uk.gov.justice.laa.datauserapi.application.command.handler;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskRejectedException;
import uk.gov.justice.laa.datauserapi.application.command.service.SilasStatusRefreshService;
import uk.gov.justice.laa.datauserapi.application.command.userprofile.RefreshSilasStatusCommand;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class RefreshSilasStatusHandlerTest {
    private final SilasStatusRefreshService refreshService = mock(SilasStatusRefreshService.class);
    private final RefreshSilasStatusHandler handler = new RefreshSilasStatusHandler(refreshService);
    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(RefreshSilasStatusHandler.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void handle_returnsAcceptedWhenRefreshIsStarted() {
        AuthenticatedEntraUser actor = AuthenticatedEntraUser.builder().id(null).entraOid("actor-oid").firstName("first").lastName("last").build();
        CommandResult result = handler.handle(new RefreshSilasStatusCommand(), actor);

        assertThat(result).isEqualTo(CommandResult.success(
                "SiLAS status refresh accepted for asynchronous processing."));
        verify(refreshService).refresh("actor-oid");
        verifyNoMoreInteractions(refreshService);
    }

    @Test
    void handle_returnsFailureWhenRefreshTaskIsRejected() {
        doThrow(new TaskRejectedException("executor full")).when(refreshService).refresh("actor-oid");

        AuthenticatedEntraUser actor = AuthenticatedEntraUser.builder().id(null).entraOid("actor-oid").firstName("first").lastName("last").build();
        CommandResult result = handler.handle(new RefreshSilasStatusCommand(), actor);

        assertThat(result).isEqualTo(CommandResult.failure(
                "SiLAS status refresh is already running or the worker is unavailable."));
        assertThat(appender.list).singleElement().satisfies(event -> {
            assertThat(event.getArgumentArray()).extracting(Object::toString)
                    .contains("exception_group=task_rejected", "exception_reason=task_rejected");
            assertThat(event.getThrowableProxy()).isNotNull();
        });
        verify(refreshService).refresh("actor-oid");
        verifyNoMoreInteractions(refreshService);
    }

    @Test
    void handle_propagatesUnexpectedFailures() {
        IllegalStateException failure = new IllegalStateException("unexpected");
        doThrow(failure).when(refreshService).refresh("actor-oid");

        AuthenticatedEntraUser actor = AuthenticatedEntraUser.builder().id(null).entraOid("actor-oid").firstName("first").lastName("last").build();
        assertThatThrownBy(() -> handler.handle(new RefreshSilasStatusCommand(), actor)).isSameAs(failure);
        verify(refreshService).refresh("actor-oid");
        verifyNoMoreInteractions(refreshService);
    }
}

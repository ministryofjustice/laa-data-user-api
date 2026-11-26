package uk.gov.justice.laa.datauserapi.application.command.handler;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskRejectedException;
import uk.gov.justice.laa.datauserapi.application.command.ccms.TriggerCcmsRoleSyncCommand;
import uk.gov.justice.laa.datauserapi.application.command.service.CcmsRoleSyncService;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TriggerCcmsRoleSyncHandlerTest {

    private final CcmsRoleSyncService service = mock(CcmsRoleSyncService.class);
    private final TriggerCcmsRoleSyncHandler handler = new TriggerCcmsRoleSyncHandler(service);
    private final AuthenticatedEntraUser actor = AuthenticatedEntraUser.builder().entraOid("actor").build();
    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(TriggerCcmsRoleSyncHandler.class);
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
    void acceptsSyncForBackgroundProcessing() {
        assertThat(handler.handle(new TriggerCcmsRoleSyncCommand(), actor))
                .isEqualTo(CommandResult.success("CCMS role sync accepted for asynchronous processing."));
        verify(service).sync("actor");
    }

    @Test
    void rejectedWorkerReturnsFailureLikeRefreshStatus() {
        doThrow(new TaskRejectedException("busy")).when(service).sync("actor");
        assertThat(handler.handle(new TriggerCcmsRoleSyncCommand(), actor))
                .isEqualTo(CommandResult.failure("CCMS role sync is already running or the worker is unavailable."));
        assertThat(appender.list).singleElement().satisfies(event -> {
            assertThat(event.getArgumentArray()).extracting(Object::toString)
                    .contains("exception_group=task_rejected", "exception_reason=task_rejected", "actorEntraOid=actor");
            assertThat(event.getThrowableProxy()).isNotNull();
        });
    }

    @Test
    void propagatesUnexpectedFailure() {
        var failure = new IllegalStateException("unexpected");
        doThrow(failure).when(service).sync("actor");

        assertThatThrownBy(() -> handler.handle(new TriggerCcmsRoleSyncCommand(), actor)).isSameAs(failure);
    }
}

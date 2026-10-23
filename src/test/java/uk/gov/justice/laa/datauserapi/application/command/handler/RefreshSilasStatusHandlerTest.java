package uk.gov.justice.laa.datauserapi.application.command.handler;

import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import uk.gov.justice.laa.datauserapi.application.command.service.SilasStatusRefreshService;
import uk.gov.justice.laa.datauserapi.application.command.userprofile.RefreshSilasStatusCommand;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class RefreshSilasStatusHandlerTest {
    private final SilasStatusRefreshService refreshService = mock(SilasStatusRefreshService.class);
    private final RefreshSilasStatusHandler handler = new RefreshSilasStatusHandler(refreshService);

    @Test
    void handle_returnsAcceptedWhenRefreshIsStarted() {
        CommandResult result = handler.handle(new RefreshSilasStatusCommand(), "actor-oid");

        assertThat(result).isEqualTo(CommandResult.success(
                "SiLAS status refresh accepted for asynchronous processing."));
        verify(refreshService).refresh("actor-oid");
        verifyNoMoreInteractions(refreshService);
    }

    @Test
    void handle_returnsFailureWhenRefreshTaskIsRejected() {
        doThrow(new TaskRejectedException("executor full")).when(refreshService).refresh("actor-oid");

        CommandResult result = handler.handle(new RefreshSilasStatusCommand(), "actor-oid");

        assertThat(result).isEqualTo(CommandResult.failure(
                "SiLAS status refresh is already running or the worker is unavailable."));
        verify(refreshService).refresh("actor-oid");
        verifyNoMoreInteractions(refreshService);
    }

    @Test
    void handle_propagatesUnexpectedFailures() {
        IllegalStateException failure = new IllegalStateException("unexpected");
        doThrow(failure).when(refreshService).refresh("actor-oid");

        assertThatThrownBy(() -> handler.handle(new RefreshSilasStatusCommand(), "actor-oid")).isSameAs(failure);
        verify(refreshService).refresh("actor-oid");
        verifyNoMoreInteractions(refreshService);
    }
}

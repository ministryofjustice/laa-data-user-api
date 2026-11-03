package uk.gov.justice.laa.datauserapi.application.command.service;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class SilasStatusRefreshServiceTest {
    private final SilasStatusRefreshBatchService batchService = mock(SilasStatusRefreshBatchService.class);
    private final SilasStatusRefreshService service = new SilasStatusRefreshService(batchService);

    @Test
    void refresh_iteratesUsingLastCursorAndAggregatesUntilEmptyBatch() {
        UUID firstCursor = UUID.randomUUID();
        UUID secondCursor = UUID.randomUUID();
        String actor = "system-actor";
        when(batchService.refreshBatch(null, actor))
                .thenReturn(new SilasStatusRefreshBatchService.BatchResult(firstCursor, 2, 3));
        when(batchService.refreshBatch(firstCursor, actor))
                .thenReturn(new SilasStatusRefreshBatchService.BatchResult(secondCursor, 4, 5));
        when(batchService.refreshBatch(secondCursor, actor))
                .thenReturn(new SilasStatusRefreshBatchService.BatchResult(null, 100, 200));

        service.refresh(actor);

        verify(batchService).refreshBatch(null, actor);
        verify(batchService).refreshBatch(firstCursor, actor);
        verify(batchService).refreshBatch(secondCursor, actor);
        verifyNoMoreInteractions(batchService);
    }

    @Test
    void refresh_stopsImmediatelyWhenFirstBatchIsEmpty() {
        when(batchService.refreshBatch(null, "actor"))
                .thenReturn(new SilasStatusRefreshBatchService.BatchResult(null, 0, 0));

        service.refresh("actor");

        verify(batchService).refreshBatch(null, "actor");
        verifyNoMoreInteractions(batchService);
    }

    @Test
    void refresh_propagatesBatchFailure() {
        RuntimeException failure = new RuntimeException("batch failed");
        when(batchService.refreshBatch(null, "actor")).thenThrow(failure);

        assertThatThrownBy(() -> service.refresh("actor")).isSameAs(failure);
        verify(batchService).refreshBatch(null, "actor");
        verifyNoMoreInteractions(batchService);
    }
}

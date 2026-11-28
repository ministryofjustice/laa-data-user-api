package uk.gov.justice.laa.datauserapi.application.query.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.datauserapi.application.query.dto.AuditUserExportBatch;
import uk.gov.justice.laa.datauserapi.application.query.dto.AuditUserExportRow;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.AuditUserExportBatchReader;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.contracts.dto.AuditUserExportCriteria;
import uk.gov.justice.laa.datauserapi.service.csv.CsvExportWriter;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Verifies the keyset paging cursor loop reads and writes one batch at a time (via the real
 * {@link CsvExportWriter}) and never accumulates the whole export in memory, rather than
 * re-testing fetching/mapping, which belongs to {@code AuditUserExportBatchReader}.
 */
@ExtendWith(MockitoExtension.class)
class AuditUserExportServiceTest {

    private static final int BATCH_SIZE = 500;

    @Mock
    private AuditUserExportBatchReader batchReader;

    private final CsvExportWriter csvExportWriter = new CsvExportWriter();

    private AuditUserExportService service;

    private static final AuditUserExportCriteria CRITERIA =
            new AuditUserExportCriteria(UserType.INTERNAL, null, null, null, null, null, null);

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new AuditUserExportService(batchReader, csvExportWriter);
    }

    @Test
    void export_withNoMatches_writesHeaderOnlyCsvAfterSingleBatchRead() throws Exception {
        when(batchReader.readBatch(eq(CRITERIA), isNull(), eq(BATCH_SIZE)))
                .thenReturn(new AuditUserExportBatch(List.of(), null, 0));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.export(CRITERIA, out);

        assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo("Name,Email,Firm name,Firm ID,Multi-firm,"
                + "Provider Admin,App Access,SiLAS Account Status,Roles Assigned\n");
        verify(batchReader).readBatch(CRITERIA, null, BATCH_SIZE);
        verifyNoMoreInteractions(batchReader);
    }

    @Test
    void export_readsEveryBatchUntilExhaustedWithoutAccumulatingFullResult() throws Exception {
        UUID firstId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID secondId = UUID.fromString("00000000-0000-0000-0000-000000000002");

        // A full-sized batch signals more may follow; the trailing short batch ends the export.
        List<AuditUserExportRow> firstRows = fullBatchOf(rowNamed("Alice"));
        AuditUserExportBatch firstBatch = new AuditUserExportBatch(firstRows, firstId, BATCH_SIZE);
        AuditUserExportBatch secondBatch = new AuditUserExportBatch(List.of(rowNamed("Bob")), secondId, 1);

        when(batchReader.readBatch(CRITERIA, null, BATCH_SIZE)).thenReturn(firstBatch);
        when(batchReader.readBatch(CRITERIA, firstId, BATCH_SIZE)).thenReturn(secondBatch);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.export(CRITERIA, out);

        String csv = out.toString(StandardCharsets.UTF_8);
        assertThat(csv).contains("Alice").contains("Bob");
        verify(batchReader).readBatch(CRITERIA, null, BATCH_SIZE);
        verify(batchReader).readBatch(CRITERIA, firstId, BATCH_SIZE);
        verifyNoMoreInteractions(batchReader);
    }

    @Test
    void export_whenFullBatchLosesRowsToConcurrentDeletion_stillReadsNextBatch() throws Exception {
        UUID firstId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID secondId = UUID.fromString("00000000-0000-0000-0000-000000000002");

        // Simulates a profile deleted between the ID query and the detail query: the batch's
        // selectedIdCount is still a full batch (more may follow) even though one fewer row was
        // actually mapped. Without the fix, the service would read rows().size() (BATCH_SIZE - 1)
        // and stop here, silently dropping Bob and anything selected after this batch.
        List<AuditUserExportRow> firstRows = fullBatchOf(rowNamed("Alice"), BATCH_SIZE - 1);
        AuditUserExportBatch firstBatch = new AuditUserExportBatch(firstRows, firstId, BATCH_SIZE);
        AuditUserExportBatch secondBatch = new AuditUserExportBatch(List.of(rowNamed("Bob")), secondId, 1);

        when(batchReader.readBatch(CRITERIA, null, BATCH_SIZE)).thenReturn(firstBatch);
        when(batchReader.readBatch(CRITERIA, firstId, BATCH_SIZE)).thenReturn(secondBatch);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.export(CRITERIA, out);

        String csv = out.toString(StandardCharsets.UTF_8);
        assertThat(csv).contains("Alice").contains("Bob");
        verify(batchReader).readBatch(CRITERIA, null, BATCH_SIZE);
        verify(batchReader).readBatch(CRITERIA, firstId, BATCH_SIZE);
        verifyNoMoreInteractions(batchReader);
    }

    private static List<AuditUserExportRow> fullBatchOf(AuditUserExportRow row) {
        return fullBatchOf(row, BATCH_SIZE);
    }

    private static List<AuditUserExportRow> fullBatchOf(AuditUserExportRow row, int count) {
        return java.util.stream.Stream.generate(() -> row).limit(count).toList();
    }

    private static AuditUserExportRow rowNamed(String name) {
        return new AuditUserExportRow(name, "email@example.com", "Firm", "F1", "No", "No", "App", "ACTIVE", "Role");
    }
}

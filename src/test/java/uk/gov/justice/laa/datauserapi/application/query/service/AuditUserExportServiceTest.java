package uk.gov.justice.laa.datauserapi.application.query.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import uk.gov.justice.laa.datauserapi.application.query.dto.AuditUserExportRow;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.AuditUserExportBatchReader;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.contracts.dto.AuditUserExportCriteria;
import uk.gov.justice.laa.datauserapi.service.csv.CsvExportWriter;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Verifies the paging cursor loop reads and writes one batch at a time (via the real
 * {@link CsvExportWriter}) and never accumulates the whole export in memory, rather than
 * re-testing fetching/mapping, which belongs to {@code AuditUserExportBatchReader}.
 */
@ExtendWith(MockitoExtension.class)
class AuditUserExportServiceTest {

    @Mock
    private AuditUserExportBatchReader batchReader;

    private final CsvExportWriter csvExportWriter = new CsvExportWriter();

    private AuditUserExportService service;

    private static final AuditUserExportCriteria CRITERIA =
            new AuditUserExportCriteria(UserType.INTERNAL, null, null, null, null, null, null);
    private static final Pageable FIRST_PAGE = PageRequest.of(0, 500);

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new AuditUserExportService(batchReader, csvExportWriter);
    }

    @Test
    void export_withNoMatches_writesHeaderOnlyCsvAfterSingleBatchRead() throws Exception {
        when(batchReader.readBatch(eq(CRITERIA), eq(FIRST_PAGE)))
                .thenReturn(new PageImpl<>(List.of(), FIRST_PAGE, 0));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.export(CRITERIA, out);

        assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo("Name,Email,Firm name,Firm ID,Multi-firm,"
                + "Provider Admin,App Access,SiLAS Account Status,Roles Assigned\n");
        verify(batchReader).readBatch(CRITERIA, FIRST_PAGE);
        verifyNoMoreInteractions(batchReader);
    }

    @Test
    void export_readsEveryPageUntilExhaustedWithoutAccumulatingFullResult() throws Exception {
        AuditUserExportRow row1 = rowNamed("Alice");
        AuditUserExportRow row2 = rowNamed("Bob");

        Pageable secondPage = FIRST_PAGE.next();
        // Total elements exceed the page size so a second page genuinely exists.
        Page<AuditUserExportRow> firstBatch = new PageImpl<>(List.of(row1), FIRST_PAGE, 501);
        Page<AuditUserExportRow> secondBatch = new PageImpl<>(List.of(row2), secondPage, 501);

        when(batchReader.readBatch(CRITERIA, FIRST_PAGE)).thenReturn(firstBatch);
        when(batchReader.readBatch(CRITERIA, secondPage)).thenReturn(secondBatch);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.export(CRITERIA, out);

        String csv = out.toString(StandardCharsets.UTF_8);
        assertThat(csv).contains("Alice").contains("Bob");
        verify(batchReader).readBatch(CRITERIA, FIRST_PAGE);
        verify(batchReader).readBatch(CRITERIA, secondPage);
        verifyNoMoreInteractions(batchReader);
    }

    private static AuditUserExportRow rowNamed(String name) {
        return new AuditUserExportRow(name, "email@example.com", "Firm", "F1", "No", "No", "App", "ACTIVE", "Role");
    }
}

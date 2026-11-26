package uk.gov.justice.laa.datauserapi.application.query.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.query.dto.AuditUserExportBatch;
import uk.gov.justice.laa.datauserapi.application.query.dto.AuditUserExportRow;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.AuditUserExportBatchReader;
import uk.gov.justice.laa.datauserapi.contracts.dto.AuditUserExportCriteria;
import uk.gov.justice.laa.datauserapi.service.csv.CsvExportWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Streams every profile matching an audit user export request straight to the response, without
 * ever holding the full result (or a single long-lived transaction) in memory.
 *
 * <p>Each batch of matching profiles is fetched and mapped by {@link AuditUserExportBatchReader}
 * in its own short read-only transaction; this class only tracks the keyset paging cursor (the
 * last-seen profile ID, not a page number — see {@link AuditUserExportBatchReader} for why) and
 * hands each batch to {@link CsvExportWriter} as soon as it arrives, so the CSV is written
 * incrementally and memory use stays bounded by a single batch, not the whole export.
 *
 * <p>{@link #export} itself opens no transaction ({@code NOT_SUPPORTED}): by the time it runs,
 * {@code ExportAuditUsersHandler} has already authorised the request, and the class-level
 * {@code @Transactional(readOnly = true)} (required for every query service) would otherwise hold
 * a transaction open for the full duration of the export.
 */
@Service
@Transactional(readOnly = true)
public class AuditUserExportService {

    private static final int BATCH_SIZE = 500;

    private final AuditUserExportBatchReader batchReader;
    private final CsvExportWriter csvExportWriter;

    public AuditUserExportService(AuditUserExportBatchReader batchReader, CsvExportWriter csvExportWriter) {
        this.batchReader = batchReader;
        this.csvExportWriter = csvExportWriter;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED, readOnly = true)
    public void export(AuditUserExportCriteria criteria, OutputStream outputStream) throws IOException {
        AtomicReference<UUID> lastSeenId = new AtomicReference<>(null);
        AtomicBoolean done = new AtomicBoolean(false);

        csvExportWriter.writeBatches(outputStream, AuditUserExportRow.class, () -> {
            if (done.get()) {
                return null;
            }

            AuditUserExportBatch batch = batchReader.readBatch(criteria, lastSeenId.get(), BATCH_SIZE);
            lastSeenId.set(batch.lastSeenId());
            if (batch.rows().size() < BATCH_SIZE) {
                done.set(true);
            }
            return batch.rows();
        });
    }
}

package uk.gov.justice.laa.datauserapi.application.query.dto;

import java.util.List;
import java.util.UUID;

/**
 * One keyset-paginated batch of audit export rows, together with the cursor needed to fetch the
 * next batch.
 *
 * <p>{@code lastSeenId} is the highest profile ID read in this batch (or {@code null} if the
 * batch was empty); it is returned alongside {@code rows} because {@link AuditUserExportRow}
 * deliberately carries no profile ID of its own, so the cursor cannot be derived from the rows.
 *
 * <p>{@code selectedIdCount} is the number of profile IDs the ID-selection query returned for
 * this batch, which can be strictly greater than {@code rows.size()}: a profile selected by that
 * query can be deleted (and so dropped during mapping) before the detail query that hydrates it
 * runs. Callers deciding whether more batches may follow must compare the requested batch size
 * against {@code selectedIdCount}, not {@code rows.size()}, or a profile deleted mid-export can
 * make an otherwise-full batch look short and stop the export early, silently omitting every
 * later matching profile.
 */
public record AuditUserExportBatch(List<AuditUserExportRow> rows, UUID lastSeenId, int selectedIdCount) {
}

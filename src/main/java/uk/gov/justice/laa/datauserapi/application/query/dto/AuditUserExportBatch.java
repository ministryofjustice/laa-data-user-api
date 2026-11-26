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
 */
public record AuditUserExportBatch(List<AuditUserExportRow> rows, UUID lastSeenId) {
}

package uk.gov.justice.laa.datauserapi.application.query.dto;

import java.util.List;
import java.util.UUID;

/**
 * One keyset-paginated batch of audit export rows, together with the cursor needed to fetch the
 * next batch.
 */
public record AuditUserExportBatch(List<AuditUserExportRow> rows, UUID lastSeenId, int selectedIdCount) {
}

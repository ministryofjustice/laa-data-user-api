package uk.gov.justice.laa.datauserapi.application.query.shared.repository;

import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.query.dto.AuditUserExportBatch;
import uk.gov.justice.laa.datauserapi.application.query.dto.AuditUserExportRow;
import uk.gov.justice.laa.datauserapi.application.query.mapper.AuditUserExportMapper;
import uk.gov.justice.laa.datauserapi.contracts.dto.AuditUserExportCriteria;

import java.util.List;
import java.util.UUID;

/**
 * Reads one keyset-paginated batch of audit export rows at a time, each in its own short
 * read-only transaction, so {@code AuditUserExportService} can stream the export without holding
 * a single transaction (or the full result) open for its entire duration.
 *
 * <p>Pagination is by last-seen profile ID, not page number: see
 * {@link AuditUserExportQueryRepository#findMatchingProfileIds} for why this is required for
 * correctness across an export that spans multiple independent transactions.
 *
 * <p>With Open EntityManager in View enabled (the Spring Boot default, left in place for the rest
 * of the application), the entities loaded by {@link AuditUserExportQueryRepository#findDetailsWithRoles}
 * would otherwise stay managed in the request-scoped persistence context for the whole export, not
 * just this batch's transaction, growing without bound as more batches are read. {@code clear()}
 * is called after mapping each batch to DTOs to detach them immediately, bounding retention to
 * roughly one batch regardless of the export's total size.
 *
 * <p>The returned {@link AuditUserExportBatch#selectedIdCount()} is the number of IDs
 * {@link AuditUserExportQueryRepository#findMatchingProfileIds} returned, not the number of rows
 * successfully mapped: a profile selected by that query can be deleted before
 * {@link AuditUserExportQueryRepository#findDetailsWithRoles} runs, so {@code rows.size()} alone
 * cannot be used to tell whether this was the export's last batch.
 *
 * <p>Lives in {@code shared.repository} (rather than {@code service}) because it is one of the
 * few places in the query side allowed to reference persistence entities directly, and because a
 * distinct Spring bean is required here: a short-lived transaction can only be opened via a
 * separate proxied bean, not a self-invoked method on the orchestrating service.
 */
@Component
public class AuditUserExportBatchReader {

    private final AuditUserExportQueryRepository repository;
    private final AuditUserExportMapper mapper;
    private final EntityManager entityManager;

    public AuditUserExportBatchReader(
            AuditUserExportQueryRepository repository,
            AuditUserExportMapper mapper,
            EntityManager entityManager) {
        this.repository = repository;
        this.mapper = mapper;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public AuditUserExportBatch readBatch(AuditUserExportCriteria criteria, UUID lastSeenId, int batchSize) {
        List<UUID> ids = repository.findMatchingProfileIds(criteria, lastSeenId, Limit.of(batchSize));

        List<AuditUserExportRow> rows = ids.isEmpty()
                ? List.of()
                : mapper.toRows(ids, repository.findDetailsWithRoles(ids));

        UUID newLastSeenId = ids.isEmpty() ? lastSeenId : ids.get(ids.size() - 1);

        entityManager.clear();

        return new AuditUserExportBatch(rows, newLastSeenId, ids.size());
    }
}

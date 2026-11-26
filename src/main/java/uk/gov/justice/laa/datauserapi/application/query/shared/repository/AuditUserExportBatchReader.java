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

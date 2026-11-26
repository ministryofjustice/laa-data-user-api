package uk.gov.justice.laa.datauserapi.application.query.shared.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.query.dto.AuditUserExportRow;
import uk.gov.justice.laa.datauserapi.application.query.mapper.AuditUserExportMapper;
import uk.gov.justice.laa.datauserapi.contracts.dto.AuditUserExportCriteria;

import java.util.List;
import java.util.UUID;

/**
 * Reads one page of audit export rows at a time, each in its own short read-only transaction, so
 * {@code AuditUserExportService} can stream the export without holding a single transaction (or
 * the full result) open for its entire duration.
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

    public AuditUserExportBatchReader(AuditUserExportQueryRepository repository, AuditUserExportMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public Page<AuditUserExportRow> readBatch(AuditUserExportCriteria criteria, Pageable pageable) {
        Page<UUID> idPage = repository.findMatchingProfileIds(criteria, pageable);
        List<UUID> ids = idPage.getContent();

        List<AuditUserExportRow> rows = ids.isEmpty()
                ? List.of()
                : mapper.toRows(ids, repository.findDetailsWithRoles(ids));

        return new PageImpl<>(rows, pageable, idPage.getTotalElements());
    }
}

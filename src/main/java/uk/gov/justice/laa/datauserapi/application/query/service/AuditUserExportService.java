package uk.gov.justice.laa.datauserapi.application.query.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.query.dto.AuditUserExportRow;
import uk.gov.justice.laa.datauserapi.application.query.mapper.AuditUserExportMapper;
import uk.gov.justice.laa.datauserapi.application.query.queryauditusersexport.ExportAuditUsersQuery;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.AuditUserExportQueryRepository;
import uk.gov.justice.laa.datauserapi.contracts.dto.AuditUserExportCriteria;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Fetches and maps every profile matching an audit user export request.
 *
 * <p>Matching is done in two steps to bound memory while avoiding both N+1 queries and
 * Hibernate's {@code MultipleBagFetchException}: a batch of matching profile IDs is paginated
 * from {@link AuditUserExportQueryRepository#findMatchingProfileIds}, then that batch's full
 * details (with {@code appRoles}/{@code offices} fetched) are loaded in one query via
 * {@link AuditUserExportQueryRepository#findDetailsWithRolesAndOffices}. This repeats until every
 * matching profile has been loaded and mapped.
 */
@Service
@Transactional(readOnly = true)
public class AuditUserExportService {

    private static final int BATCH_SIZE = 500;

    private final AuditUserExportQueryRepository repository;
    private final AuditUserExportMapper mapper;

    public AuditUserExportService(AuditUserExportQueryRepository repository, AuditUserExportMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public List<AuditUserExportRow> export(ExportAuditUsersQuery query) {
        AuditUserExportCriteria criteria = query.criteria();
        List<AuditUserExportRow> rows = new ArrayList<>();

        Pageable pageable = PageRequest.of(0, BATCH_SIZE);
        Page<UUID> page = repository.findMatchingProfileIds(criteria, pageable);
        rows.addAll(mapBatch(page.getContent()));

        while (page.hasNext()) {
            page = repository.findMatchingProfileIds(criteria, page.nextPageable());
            rows.addAll(mapBatch(page.getContent()));
        }

        return rows;
    }

    private List<AuditUserExportRow> mapBatch(List<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }

        return mapper.toRows(ids, repository.findDetailsWithRolesAndOffices(ids));
    }
}

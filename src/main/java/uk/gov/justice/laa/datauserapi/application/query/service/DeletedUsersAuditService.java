package uk.gov.justice.laa.datauserapi.application.query.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.query.dto.DeletedUserAuditRow;
import uk.gov.justice.laa.datauserapi.application.query.mapper.AuditQueryMapper;
import uk.gov.justice.laa.datauserapi.application.query.queryaudit.DeletedUsersAuditQuery;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.AuditQueryRepository;
import uk.gov.justice.laa.datauserapi.contracts.dto.AuditDeletedUsersCriteria;
import uk.gov.justice.laa.datauserapi.contracts.response.DeletedUserAuditPage;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.model.Permission;
import uk.gov.justice.laa.datauserapi.security.rbac.ActorPermissions;

import java.util.Locale;

/**
 * Pages deleted-user audit snapshots. Records are treated as EXTERNAL, so an actor without
 * {@code VIEW_EXTERNAL_USER} sees an empty page.
 */
@Service
@Transactional(readOnly = true)
public class DeletedUsersAuditService {

    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_PAGE_SIZE = 100;
    private static final String MATCH_ALL = "%";

    private final AuditQueryRepository auditRepository;
    private final AuditQueryMapper auditMapper;

    public DeletedUsersAuditService(AuditQueryRepository auditRepository, AuditQueryMapper auditMapper) {
        this.auditRepository = auditRepository;
        this.auditMapper = auditMapper;
    }

    public DeletedUserAuditPage getDeletedUsers(DeletedUsersAuditQuery query, AuthenticatedEntraUser actor) {
        AuditDeletedUsersCriteria criteria = query.criteria();
        int pageNumber = criteria.pageNumber() == null ? 0 : criteria.pageNumber();
        int pageSize = criteria.pageSize() == null ? DEFAULT_PAGE_SIZE : Math.min(criteria.pageSize(), MAX_PAGE_SIZE);

        Pageable pageable = PageRequest.of(pageNumber, pageSize);
        // Deletion snapshots do not record the user type. Only non-internal users can be deleted through this API,
        // so every deleted record is treated as EXTERNAL and needs VIEW_EXTERNAL_USER to be visible.
        Page<DeletedUserAuditRow> page = ActorPermissions.has(actor, Permission.VIEW_EXTERNAL_USER)
                ? auditRepository.findDeletedUsers(toEmailPattern(criteria.emailQuery()), pageable)
                : Page.empty(pageable);
        return auditMapper.toDeletedPage(page);
    }

    private String toEmailPattern(String emailQuery) {
        if (emailQuery == null || emailQuery.isBlank()) {
            return MATCH_ALL;
        }
        String escaped = emailQuery.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}

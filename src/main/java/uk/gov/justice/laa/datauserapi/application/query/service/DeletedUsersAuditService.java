package uk.gov.justice.laa.datauserapi.application.query.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.query.dto.DeletedUserAuditRow;
import uk.gov.justice.laa.datauserapi.application.query.mapper.AuditQueryMapper;
import uk.gov.justice.laa.datauserapi.application.query.queryaudit.DeletedUsersAuditQuery;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.AuditQueryRepository;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.contracts.dto.AuditDeletedUsersCriteria;
import uk.gov.justice.laa.datauserapi.contracts.response.DeletedUserAuditPage;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.model.Permission;
import uk.gov.justice.laa.datauserapi.security.rbac.ActorPermissions;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Pages deleted-user audit snapshots. Deleted INTERNAL records are visible to any internal actor allowed to reach the
 * endpoint; deleted EXTERNAL records require {@code VIEW_EXTERNAL_USER}.
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

        Page<DeletedUserAuditRow> page = auditRepository.findDeletedUsers(
                visibleUserTypes(actor), toEmailPattern(criteria.emailQuery()), PageRequest.of(pageNumber, pageSize));
        return auditMapper.toDeletedPage(page);
    }

    private Set<UserType> visibleUserTypes(AuthenticatedEntraUser actor) {
        Set<UserType> visible = EnumSet.of(UserType.INTERNAL);
        if (ActorPermissions.has(actor, Permission.VIEW_EXTERNAL_USER)) {
            visible.add(UserType.EXTERNAL);
        }
        return visible;
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

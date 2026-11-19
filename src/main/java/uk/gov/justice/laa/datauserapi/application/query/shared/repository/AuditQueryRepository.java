package uk.gov.justice.laa.datauserapi.application.query.shared.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import uk.gov.justice.laa.datauserapi.application.query.dto.AuditStatusHistoryRow;
import uk.gov.justice.laa.datauserapi.application.query.dto.DeletedUserAuditRow;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.entity.UserAccountStatusAudit;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@org.springframework.stereotype.Repository
public interface AuditQueryRepository extends Repository<UserAccountStatusAudit, UUID> {

    @Query("""
        SELECT new uk.gov.justice.laa.datauserapi.application.query.dto.AuditStatusHistoryRow(
            eu.entraOid,
            a.userAccountAuditStatus,
            a.statusChangedDate,
            a.statusChangedBy,
            r.name,
            r.description
        )
        FROM UserAccountStatusAudit a
        JOIN EntraUser eu ON a.entraUserId = eu.id
        LEFT JOIN a.deactivateUserReasonLookup r
        WHERE eu.entraOid = :userEntraObjectId
        ORDER BY a.statusChangedDate DESC, a.id DESC
        """)
    List<AuditStatusHistoryRow> findStatusHistory(@Param("userEntraObjectId") String userEntraObjectId,
                                                  Pageable pageable);

    /**
     * Pages deleted-user snapshots whose recorded user type is in {@code visibleUserTypes}. Records without a
     * recorded type never match. {@code emailPattern} is a lower-case LIKE pattern using {@code \} as the escape.
     */
    @Query(value = """
        SELECT new uk.gov.justice.laa.datauserapi.application.query.dto.DeletedUserAuditRow(
            a.deletedUserEntraOid,
            a.userName,
            a.userEmail,
            a.statusChangedBy,
            a.statusChangedDate,
            r.code,
            r.label
        )
        FROM UserAccountStatusAudit a
        LEFT JOIN a.deleteUserReasonEntity r
        WHERE a.userAccountAuditStatus = uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountAuditStatus.DELETED
            AND a.deletedUserType IN :visibleUserTypes
            AND COALESCE(LOWER(a.userEmail), '') LIKE :emailPattern ESCAPE '\\'
        ORDER BY a.statusChangedDate DESC, a.id DESC
        """,
        countQuery = """
        SELECT COUNT(a)
        FROM UserAccountStatusAudit a
        WHERE a.userAccountAuditStatus = uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountAuditStatus.DELETED
            AND a.deletedUserType IN :visibleUserTypes
            AND COALESCE(LOWER(a.userEmail), '') LIKE :emailPattern ESCAPE '\\'
        """)
    Page<DeletedUserAuditRow> findDeletedUsers(@Param("visibleUserTypes") Collection<UserType> visibleUserTypes,
                                               @Param("emailPattern") String emailPattern,
                                               Pageable pageable);
}

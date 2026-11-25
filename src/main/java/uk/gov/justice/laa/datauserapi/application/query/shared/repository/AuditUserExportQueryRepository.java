package uk.gov.justice.laa.datauserapi.application.query.shared.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import uk.gov.justice.laa.datauserapi.contracts.dto.AuditUserExportCriteria;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;

import java.util.List;
import java.util.UUID;

/**
 * Profile-level filtering for the audit user CSV export.
 *
 * <p>Deliberately separate from {@link EntraUserQueryRepository#searchUsers}, which works at the
 * account level and returns every profile for a matched account; the export must instead show a
 * multi-firm user only in the single firm (or INTERNAL) context being exported.
 *
 * <p>Filtering and detail-loading are split in two steps to avoid both N+1 queries and Hibernate's
 * {@code MultipleBagFetchException}/in-memory pagination problems that come from paginating a
 * query that already {@code JOIN FETCH}es multiple collections ({@code appRoles}, {@code offices}):
 * {@link #findMatchingProfileIds} applies every filter and returns a page of matching profile IDs
 * only; {@link #findDetailsWithRolesAndOffices} then batch-loads full details, with fetched
 * collections, for a given set of IDs.
 */
@org.springframework.stereotype.Repository
public interface AuditUserExportQueryRepository extends Repository<UserProfile, UUID> {

    @Query(
            value = """
                SELECT DISTINCT up.id
                FROM UserProfile up
                LEFT JOIN up.firm f
                LEFT JOIN up.entraUser u
                LEFT JOIN up.appRoles ar
                LEFT JOIN up.offices o
                WHERE (:#{#criteria.userType} IS NULL OR up.userType = :#{#criteria.userType})
                  AND (:#{#criteria.firmId} IS NULL OR f.code = :#{#criteria.firmId})
                  AND (:#{#criteria.userAccountStatus} IS NULL OR u.userAccountStatus = :#{#criteria.userAccountStatus})
                  AND (:#{#criteria.officeId} IS NULL OR o.code = :#{#criteria.officeId})
                  AND (:#{#criteria.appId} IS NULL OR (ar IS NOT NULL AND ar.app.id = :#{#criteria.appId}))
                  AND (:#{#criteria.appRoleId} IS NULL OR (ar IS NOT NULL AND ar.id = :#{#criteria.appRoleId}))
                  AND (:#{#criteria.neverActivatedFilter} IS NULL OR :#{#criteria.neverActivatedFilter} = FALSE
                      OR u.userAccountStatus = uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountStatus.ACTIVATION_REQUIRED)
                ORDER BY up.id ASC
                """,
            countQuery = """
                SELECT COUNT(DISTINCT up.id)
                FROM UserProfile up
                LEFT JOIN up.firm f
                LEFT JOIN up.entraUser u
                LEFT JOIN up.appRoles ar
                LEFT JOIN up.offices o
                WHERE (:#{#criteria.userType} IS NULL OR up.userType = :#{#criteria.userType})
                  AND (:#{#criteria.firmId} IS NULL OR f.code = :#{#criteria.firmId})
                  AND (:#{#criteria.userAccountStatus} IS NULL OR u.userAccountStatus = :#{#criteria.userAccountStatus})
                  AND (:#{#criteria.officeId} IS NULL OR o.code = :#{#criteria.officeId})
                  AND (:#{#criteria.appId} IS NULL OR (ar IS NOT NULL AND ar.app.id = :#{#criteria.appId}))
                  AND (:#{#criteria.appRoleId} IS NULL OR (ar IS NOT NULL AND ar.id = :#{#criteria.appRoleId}))
                  AND (:#{#criteria.neverActivatedFilter} IS NULL OR :#{#criteria.neverActivatedFilter} = FALSE
                      OR u.userAccountStatus = uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountStatus.ACTIVATION_REQUIRED)
                """
    )
    Page<UUID> findMatchingProfileIds(@Param("criteria") AuditUserExportCriteria criteria, Pageable pageable);

    @Query("""
        SELECT DISTINCT up
        FROM UserProfile up
        JOIN FETCH up.entraUser u
        LEFT JOIN FETCH up.firm f
        LEFT JOIN FETCH up.appRoles ar
        LEFT JOIN FETCH ar.app a
        LEFT JOIN FETCH up.offices o
        WHERE up.id IN :ids
        """)
    List<UserProfile> findDetailsWithRolesAndOffices(@Param("ids") List<UUID> ids);
}

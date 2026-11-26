package uk.gov.justice.laa.datauserapi.application.query.shared.repository;

import org.springframework.data.domain.Limit;
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
 * {@link #findMatchingProfileIds} applies every filter and returns a batch of matching profile IDs
 * only; {@link #findDetailsWithRoles} then batch-loads full details, with {@code appRoles} fetched,
 * for a given set of IDs. {@code offices} is never fetched there since the export never reads it;
 * office filtering happens entirely in {@link #findMatchingProfileIds}.
 *
 * <p>{@link #findMatchingProfileIds} uses keyset ("seek") pagination, not an offset {@code Page}:
 * each call returns up to {@code limit} IDs greater than {@code lastSeenId}, ordered ascending.
 * Unlike offset pagination, this remains correct when profiles are inserted or deleted between
 * batches of the same export, since each batch is defined relative to the last ID actually seen,
 * not a row count that can shift under concurrent writes.
 */
@org.springframework.stereotype.Repository
public interface AuditUserExportQueryRepository extends Repository<UserProfile, UUID> {

    @Query("""
                SELECT DISTINCT up.id
                FROM UserProfile up
                LEFT JOIN up.firm f
                LEFT JOIN up.entraUser u
                LEFT JOIN up.appRoles ar
                WHERE (:#{#criteria.userType} IS NULL OR up.userType = :#{#criteria.userType})
                  AND (:#{#criteria.firmId} IS NULL OR f.code = :#{#criteria.firmId})
                  AND (:#{#criteria.userAccountStatus} IS NULL OR u.userAccountStatus = :#{#criteria.userAccountStatus})
                  AND (:#{#criteria.officeId} IS NULL OR EXISTS (
                      SELECT requestedOffice.id FROM Office requestedOffice
                      WHERE requestedOffice.firm = up.firm AND requestedOffice.code = :#{#criteria.officeId}
                        AND (up.unrestrictedOfficeAccess = TRUE OR requestedOffice MEMBER OF up.offices)))
                  AND (:#{#criteria.appId} IS NULL OR (ar IS NOT NULL AND ar.app.id = :#{#criteria.appId}))
                  AND (:#{#criteria.appRoleId} IS NULL OR (ar IS NOT NULL AND ar.id = :#{#criteria.appRoleId}))
                  AND (:#{#criteria.neverActivatedFilter} IS NULL OR :#{#criteria.neverActivatedFilter} = FALSE
                      OR u.userAccountStatus = uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountStatus.ACTIVATION_REQUIRED)
                  AND (:lastSeenId IS NULL OR up.id > :lastSeenId)
                ORDER BY up.id ASC
                """)
    List<UUID> findMatchingProfileIds(
            @Param("criteria") AuditUserExportCriteria criteria,
            @Param("lastSeenId") UUID lastSeenId,
            Limit limit);

    @Query("""
        SELECT DISTINCT up
        FROM UserProfile up
        JOIN FETCH up.entraUser u
        LEFT JOIN FETCH up.firm f
        LEFT JOIN FETCH up.appRoles ar
        LEFT JOIN FETCH ar.app a
        WHERE up.id IN :ids
        """)
    List<UserProfile> findDetailsWithRoles(@Param("ids") List<UUID> ids);
}

package uk.gov.justice.laa.datauserapi.application.query.shared.repository;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import uk.gov.justice.laa.datauserapi.contracts.dto.AuditUserExportCriteria;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;

import java.util.List;
import java.util.UUID;

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

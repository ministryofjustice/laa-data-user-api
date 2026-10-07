package uk.gov.justice.laa.datauserapi.application.query.shared.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.datauserapi.contracts.dto.UserSearchCriteria;
import uk.gov.justice.laa.datauserapi.application.query.dto.UserAccountStatusView;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;

import java.util.Optional;
import java.util.UUID;

@Component
public interface EntraUserQueryRepository extends Repository<EntraUser, UUID> {

    @Query("""
        SELECT new uk.gov.justice.laa.datauserapi.application.query.dto.UserAccountStatusView(
            u.entraOid,
            u.email,
            u.userAccountStatus,
            u.lastModified
        )
        FROM EntraUser u
        WHERE u.id = :userEntraObjectId
        """)
    Optional<UserAccountStatusView> findStatusByUserEntraObjectId(
            @Param("userEntraObjectId") UUID userEntraObjectId);

    @Query("""
    SELECT DISTINCT u
    FROM EntraUser u
        JOIN FETCH u.userProfiles up
        LEFT JOIN FETCH up.appRoles ar
        LEFT JOIN FETCH up.offices o
    WHERE u.entraOid = :userEntraObjectId
      AND up.activeProfile = true
        """)
    Optional<EntraUser> findUserAccountSummaryByUserEntraObjectId(
            @Param("userEntraObjectId") String userEntraObjectId);

    Optional<EntraUser> findByEntraOid(String entraOid);

    @Query(
            value = """
                SELECT DISTINCT u
                FROM EntraUser u JOIN u.userProfiles up LEFT JOIN up.appRoles ar LEFT JOIN up.firm f
                WHERE((:#{#criteria.actorInternal()} = TRUE AND up.userType = UserType.INTERNAL)
                    OR
                    (:#{#criteria.actorExternal()} = TRUE AND up.firm.id = :#{#criteria.actorFirmId})
                    OR
                    (:#{#criteria.actorInternal()} = TRUE AND :#{#criteria.canViewExternalUsers} = TRUE AND up.userType = UserType.EXTERNAL))

                AND (:#{#criteria.user} IS NULL
                    OR :#{#criteria.user} = ''
                    OR LOWER(u.email) LIKE LOWER(CONCAT('%', :#{#criteria.user}, '%'))
                    OR LOWER(CONCAT(u.firstName, ' ', u.lastName)) LIKE LOWER(CONCAT('%', :#{#criteria.user}, '%')))

                AND (:#{#criteria.firmId} IS NULL OR up.firm.id = :#{#criteria.firmId})

                AND (:#{#criteria.firm} IS NULL OR :#{#criteria.firm} = '' OR LOWER(f.name) LIKE LOWER(CONCAT('%', :#{#criteria.firm}, '%'))
                                OR CAST(f.id AS STRING) LIKE CONCAT('%', :#{#criteria.firm}, '%'))
                
                AND (:#{#criteria.userType} IS NULL OR up.userType = :#{#criteria.userType})

                AND (:#{#criteria.userAccountStatus} IS NULL OR u.userAccountStatus = :#{#criteria.userAccountStatus})

                AND (:#{#criteria.appId} IS NULL OR ar.app.id = :#{#criteria.appId})

                AND( :#{#criteria.neverActivatedFilter} IS NULL OR :#{#criteria.neverActivatedFilter} = FALSE
                                OR u.userAccountStatus = UserAccountStatus.ACTIVATION_REQUIRED)

                AND (:#{#criteria.appRoleId} IS NULL OR ar.id = :#{#criteria.appRoleId})

                """,
            countQuery = """
                SELECT COUNT(DISTINCT u)
                FROM EntraUser u
                    JOIN u.userProfiles up
                    LEFT JOIN up.appRoles ar
                    LEFT JOIN up.firm f

                WHERE
                (
                    (:#{#criteria.actorInternal()} = TRUE AND up.userType = UserType.INTERNAL)
                    OR
                    (:#{#criteria.actorExternal()} = TRUE AND up.firm.id = :#{#criteria.actorFirmId})
                    OR
                    (:#{#criteria.actorInternal()} = TRUE AND :#{#criteria.canViewExternalUsers} = TRUE AND up.userType = UserType.EXTERNAL)
                )

                AND (:#{#criteria.user} IS NULL
                    OR :#{#criteria.user} = ''
                    OR LOWER(u.email)
                        LIKE LOWER(CONCAT('%', :#{#criteria.user}, '%'))
                    OR LOWER(CONCAT(u.firstName, ' ', u.lastName))
                        LIKE LOWER(CONCAT('%', :#{#criteria.user}, '%')))

                AND (:#{#criteria.firmId} IS NULL OR up.firm.id = :#{#criteria.firmId})

                AND (:#{#criteria.firm} IS NULL OR :#{#criteria.firm} = '' OR LOWER(f.name) LIKE LOWER(CONCAT('%', :#{#criteria.firm}, '%'))
                                 OR CAST(f.id AS STRING) LIKE CONCAT('%', :#{#criteria.firm}, '%'))

                AND (:#{#criteria.userType} IS NULL OR up.userType = :#{#criteria.userType})

                AND (:#{#criteria.userAccountStatus} IS NULL OR u.userAccountStatus = :#{#criteria.userAccountStatus})

                AND (:#{#criteria.appId} IS NULL OR ar.app.id = :#{#criteria.appId})

                AND( :#{#criteria.neverActivatedFilter} IS NULL OR :#{#criteria.neverActivatedFilter} = FALSE OR u.userAccountStatus = UserAccountStatus.ACTIVATION_REQUIRED)


                AND (:#{#criteria.appRoleId} IS NULL OR ar.id = :#{#criteria.appRoleId})
                """
    )
    Page<EntraUser> searchUsers(
            @Param("criteria") UserSearchCriteria criteria,
            Pageable pageable);
}
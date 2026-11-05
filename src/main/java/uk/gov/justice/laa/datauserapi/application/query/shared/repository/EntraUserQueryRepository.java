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
        WHERE u.entraOid = :userEntraObjectId
        """)
    Optional<UserAccountStatusView> findStatusByUserEntraObjectId(
            @Param("userEntraObjectId") String userEntraObjectId);

    Optional<EntraUser> findByEntraOid(String entraOid);

    @Query(
            value = """
                SELECT DISTINCT u
                FROM EntraUser u LEFT JOIN u.userProfiles up LEFT JOIN up.appRoles ar LEFT JOIN ar.app a LEFT JOIN up.firm f
                WHERE((:#{#criteria.actorInternal()} = TRUE AND up.userType = UserType.INTERNAL)
                OR
                  (:#{#criteria.actorExternal()} = TRUE AND up.firm.id = :#{#criteria.actorFirmId})
                OR
                  (:#{#criteria.actorInternal()} = TRUE AND :#{#criteria.canViewExternalUsers} = TRUE AND (u.multiFirmUser = true OR up IS NULL OR up.userType = UserType.EXTERNAL)))
                AND (:#{#criteria.user} IS NULL
                    OR :#{#criteria.user} = ''
                    OR LOWER(u.email) LIKE LOWER(CONCAT('%', :#{#criteria.user}, '%'))
                    OR LOWER(CONCAT(u.firstName, ' ', u.lastName)) LIKE LOWER(CONCAT('%', :#{#criteria.user}, '%')))
                
                AND (:#{#criteria.emailQuery} IS NULL
                    OR :#{#criteria.emailQuery} = ''
                    OR LOWER(u.email) LIKE LOWER(
                    CONCAT('%', :#{#criteria.emailQuery}, '%')))
                
                AND (:#{#criteria.firmId} IS NULL OR up.firm.code = :#{#criteria.firmId})

                AND (:#{#criteria.firm} IS NULL OR :#{#criteria.firm} = '' OR LOWER(f.name) LIKE LOWER(CONCAT('%', :#{#criteria.firm}, '%'))
                                OR CAST(f.id AS STRING) LIKE CONCAT('%', :#{#criteria.firm}, '%'))
                
                AND (:#{#criteria.userType} IS NULL OR up.userType = :#{#criteria.userType})

                AND (:#{#criteria.userAccountStatus} IS NULL OR u.userAccountStatus = :#{#criteria.userAccountStatus})

                AND (:#{#criteria.appId} IS NULL OR ar IS NOT NULL AND ar.app.id = :#{#criteria.appId})
                
                AND (:#{#criteria.appEntraClientId} IS NULL OR ar IS NOT NULL AND ar.app.entraAppId = :#{#criteria.appEntraClientId})
                
                AND (:#{#criteria.appRoleId} IS NULL OR ar IS NOT NULL AND ar.id = :#{#criteria.appRoleId})

                AND( :#{#criteria.neverActivatedFilter} IS NULL OR :#{#criteria.neverActivatedFilter} = FALSE OR u.userAccountStatus = UserAccountStatus.ACTIVATION_REQUIRED)

                """,
            countQuery = """
                SELECT COUNT(DISTINCT u)
                FROM EntraUser u LEFT JOIN u.userProfiles up LEFT JOIN up.appRoles ar LEFT JOIN ar.app a LEFT JOIN up.firm f
                WHERE((:#{#criteria.actorInternal()} = TRUE AND up.userType = UserType.INTERNAL)
                OR
                    (:#{#criteria.actorExternal()} = TRUE AND up.firm.id = :#{#criteria.actorFirmId})
                OR
                    (:#{#criteria.actorInternal()} = TRUE AND :#{#criteria.canViewExternalUsers} = TRUE AND (u.multiFirmUser = true OR up IS NULL OR up.userType = UserType.EXTERNAL)))

                AND (:#{#criteria.user} IS NULL
                    OR :#{#criteria.user} = ''
                    OR LOWER(u.email)
                        LIKE LOWER(CONCAT('%', :#{#criteria.user}, '%'))
                    OR LOWER(CONCAT(u.firstName, ' ', u.lastName))
                        LIKE LOWER(CONCAT('%', :#{#criteria.user}, '%')))
               
                AND (:#{#criteria.emailQuery} IS NULL
                    OR :#{#criteria.emailQuery} = ''
                    OR LOWER(u.email) LIKE LOWER(CONCAT('%', :#{#criteria.emailQuery}, '%')))

                AND (:#{#criteria.firmId} IS NULL OR up.firm.code = :#{#criteria.firmId})

                AND (:#{#criteria.firm} IS NULL OR :#{#criteria.firm} = '' OR LOWER(f.name) LIKE LOWER(CONCAT('%', :#{#criteria.firm}, '%'))
                                 OR CAST(f.id AS STRING) LIKE CONCAT('%', :#{#criteria.firm}, '%'))

                AND (:#{#criteria.userType} IS NULL OR up.userType = :#{#criteria.userType})

                AND (:#{#criteria.userAccountStatus} IS NULL OR u.userAccountStatus = :#{#criteria.userAccountStatus})

                AND (:#{#criteria.appId} IS NULL OR ar IS NOT NULL AND ar.app.id = :#{#criteria.appId})
                
                AND (:#{#criteria.appEntraClientId} IS NULL OR ar IS NOT NULL AND ar.app.entraAppId = :#{#criteria.appEntraClientId})
           
                AND (:#{#criteria.appRoleId} IS NULL OR ar IS NOT NULL AND ar.id = :#{#criteria.appRoleId})
               
                AND( :#{#criteria.neverActivatedFilter} IS NULL OR :#{#criteria.neverActivatedFilter} = FALSE OR u.userAccountStatus = UserAccountStatus.ACTIVATION_REQUIRED)
                """
    )
    Page<EntraUser> searchUsers(
            @Param("criteria") UserSearchCriteria criteria,
            Pageable pageable);
}

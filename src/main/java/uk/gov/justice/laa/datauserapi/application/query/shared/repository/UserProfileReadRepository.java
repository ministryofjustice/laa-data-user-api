package uk.gov.justice.laa.datauserapi.application.query.shared.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uk.gov.justice.laa.datauserapi.application.query.getuserprofilebyid.GetUserProfileByIdQuery;
import uk.gov.justice.laa.datauserapi.application.query.getuserprofiles.GetUserProfilesQuery;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;

import java.util.Optional;
import java.util.UUID;

public interface UserProfileReadRepository extends JpaRepository<UserProfile, UUID> {

    @Query("""
        SELECT userProfile FROM UserProfile userProfile
        JOIN FETCH userProfile.entraUser entraUser
        LEFT JOIN userProfile.appRoles appRoles
        LEFT JOIN userProfile.offices offices
        LEFT JOIN userProfile.firm firm
        /* User Type */
        WHERE (:#{#query.userType} IS NULL OR userProfile.userType = :#{#query.userType})
        /* User Profile Status */
        AND (:#{#query.userProfileStatus} IS NULL OR userProfile.userProfileStatus = :#{#query.userProfileStatus})
        /* App Id */
        AND (:#{#query.appId} IS NULL OR appRoles.app.entraOid = :#{#query.appId})
        /* App Role Id */
        AND (:#{#query.appRoleId} IS NULL OR appRoles.id = :#{#query.appRoleId})
        /* Office Id */
        AND (:#{#query.officeId} IS NULL OR offices.id = :#{#query.officeId})
        /* Firm Id */
        AND (:#{#query.firmId} IS NULL OR firm.id = :#{#query.firmId})
        /* Full name and Email */
        AND (:#{#query.user} IS NULL OR LOWER(CONCAT(entraUser.firstName, CONCAT(' ', entraUser.lastName))) = LOWER(:#{#query.user}) OR LOWER(entraUser.email) = LOWER(:#{#query.user}))
        /* Firm Name */
        AND (:#{#query.firm} IS NULL OR firm.name = :#{#query.firm})
        /* Firm Admin (is an external user with any permissions) */
        AND (:#{#query.firmAdmin} IS NULL OR (:#{#query.firmAdmin} = true AND userProfile.userType = 'EXTERNAL' AND appRoles.permissions IS NOT EMPTY) OR (:#{#query.firmAdmin} = false AND userProfile.userType = 'EXTERNAL' AND appRoles.permissions IS EMPTY))
        /* Multi Firm User */
        AND (:#{#query.multiFirmUser} IS NULL OR entraUser.multiFirmUser = :#{#query.multiFirmUser})
    """)
    Page<UserProfile> findAll(@Param("query") GetUserProfilesQuery query, Pageable pageable);

    @Query("""
        SELECT userProfile FROM UserProfile userProfile
        JOIN FETCH userProfile.entraUser
        LEFT JOIN FETCH userProfile.appRoles
        LEFT JOIN FETCH userProfile.offices
        LEFT JOIN FETCH userProfile.firm
        WHERE userProfile.id = :#{#query.id}
    """)
    Optional<UserProfile> findById(@Param("query") GetUserProfileByIdQuery query);

}


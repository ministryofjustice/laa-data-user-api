package uk.gov.justice.laa.datauserapi.application.query.shared.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import uk.gov.justice.laa.datauserapi.contracts.dto.UserProfileSummaryView;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@org.springframework.stereotype.Repository
public interface UserProfileQueryRepository extends Repository<EntraUser, UUID> {

    @Query("""
        SELECT new uk.gov.justice.laa.datauserapi.contracts.dto.UserProfileSummaryView(
            up.id,
            up.activeProfile,
            up.userType,
            u.email,
            CONCAT(u.firstName, ' ', u.lastName),
            COALESCE(f.name, ''),
            COALESCE(CAST(f.id AS STRING), ''),
            u.multiFirmUser,
            u.userAccountStatus,
            up.userProfileStatus,
            CASE WHEN SIZE(up.appRoles) > 0 THEN true ELSE false END
        )
        FROM EntraUser u
        JOIN u.userProfiles up
        LEFT JOIN up.firm f
        JOIN EntraUser actor ON actor.entraOid = :actorOid
        JOIN actor.userProfiles actorProfile
        WHERE u.entraOid = :userEntraObjectId
            AND actorProfile.activeProfile = true
            AND (
                (actorProfile.userType = UserType.INTERNAL AND up.userType = UserType.INTERNAL)
                OR (actorProfile.userType = UserType.EXTERNAL AND f.id = actorProfile.firm.id)
                OR (actorProfile.userType = UserType.INTERNAL 
                    AND EXISTS (
                        SELECT 1 FROM actorProfile.appRoles ar
                        JOIN ar.permissions p
                        WHERE p = uk.gov.justice.laa.datauserapi.model.Permission.VIEW_EXTERNAL_USER
                    ))
            )
        """)
    List<UserProfileSummaryView> findAllProfilesByUserEntraObjectId(
            @Param("userEntraObjectId") String userEntraObjectId,
            @Param("actorOid") String actorOid);

    @Query("""
        SELECT DISTINCT up
        FROM UserProfile up
        JOIN FETCH up.entraUser u
        LEFT JOIN FETCH up.appRoles ar
        LEFT JOIN FETCH up.offices o
        LEFT JOIN FETCH up.firm f
        JOIN EntraUser actor ON actor.entraOid = :actorOid
        JOIN actor.userProfiles actorProfile
        WHERE u.entraOid = :userEntraObjectId
            AND up.activeProfile = true
            AND actorProfile.activeProfile = true
            AND (
                (actorProfile.userType = UserType.INTERNAL AND up.userType = UserType.INTERNAL)
                OR (actorProfile.userType = UserType.EXTERNAL AND f.id = actorProfile.firm.id)
                OR (actorProfile.userType = UserType.INTERNAL 
                    AND EXISTS (
                        SELECT 1 FROM actorProfile.appRoles ar
                        JOIN ar.permissions p
                        WHERE p = uk.gov.justice.laa.datauserapi.model.Permission.VIEW_EXTERNAL_USER                    ))
            )
        """)
    Optional<UserProfile> findActiveProfileWithDetailsAndRoles(
            @Param("userEntraObjectId") String userEntraObjectId,
            @Param("actorOid") String actorOid);
}


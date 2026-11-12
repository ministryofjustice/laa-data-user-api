package uk.gov.justice.laa.datauserapi.application.query.shared.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import uk.gov.justice.laa.datauserapi.application.query.dto.ProfileVisibilityContext;
import uk.gov.justice.laa.datauserapi.contracts.dto.AppView;
import uk.gov.justice.laa.datauserapi.contracts.dto.OfficeView;
import uk.gov.justice.laa.datauserapi.entity.AppRole;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;

/** Reads the roles, applications and offices of a single user profile, always keyed on the profile ID. */
@org.springframework.stereotype.Repository
public interface UserProfileAccessQueryRepository extends Repository<UserProfile, UUID> {

    @Query("""
        SELECT new uk.gov.justice.laa.datauserapi.application.query.dto.ProfileVisibilityContext(
            true, up.userType, f.id)
        FROM UserProfile up
        LEFT JOIN up.firm f
        WHERE up.id = :profileId
        """)
    Optional<ProfileVisibilityContext> findVisibilityContextById(@Param("profileId") UUID profileId);

    @Query("""
        SELECT ar
        FROM UserProfile up
        JOIN up.appRoles ar
        JOIN FETCH ar.app a
        WHERE up.id = :profileId
            AND (:appId IS NULL OR a.id = :appId)
            AND (:appEntraClientId IS NULL OR LOWER(a.entraAppId) = :appEntraClientId)
        ORDER BY a.ordinal, a.name, ar.ordinal, ar.name, ar.id
        """)
    List<AppRole> findAppRolesByProfileId(
            @Param("profileId") UUID profileId,
            @Param("appId") UUID appId,
            @Param("appEntraClientId") String appEntraClientId,
            Pageable pageable);

    @Query("""
        SELECT new uk.gov.justice.laa.datauserapi.contracts.dto.AppView(
            CAST(a.id AS STRING), a.name, a.description, a.url, a.enabled, a.ordinal, a.entraOid, a.entraAppId)
        FROM App a
        WHERE a.enabled = true
            AND a.id IN (
                SELECT ar.app.id
                FROM UserProfile up
                JOIN up.appRoles ar
                WHERE up.id = :profileId)
        ORDER BY a.ordinal, a.name, a.id
        """)
    List<AppView> findEnabledAppsByProfileId(@Param("profileId") UUID profileId, Pageable pageable);

    @Query("""
        SELECT new uk.gov.justice.laa.datauserapi.contracts.dto.OfficeView(
            CAST(o.id AS STRING), CAST(f.id AS STRING),
            o.address.postcode, o.address.addressLine1, o.address.addressLine2, o.address.addressLine3, o.address.city)
        FROM UserProfile up
        JOIN up.firm f
        JOIN Office o ON o.firm = f
        WHERE up.id = :profileId
            AND (up.unrestrictedOfficeAccess = true OR o MEMBER OF up.offices)
        ORDER BY o.code, o.id
        """)
    List<OfficeView> findAccessibleOfficesByProfileId(@Param("profileId") UUID profileId, Pageable pageable);
}

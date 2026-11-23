package uk.gov.justice.laa.datauserapi.application.query.shared.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import uk.gov.justice.laa.datauserapi.contracts.dto.AppView;
import uk.gov.justice.laa.datauserapi.entity.App;
import uk.gov.justice.laa.datauserapi.entity.AppRole;

/** Reads the catalogue of active applications and their roles, always keyed on the internal SiLAS IDs. */
@org.springframework.stereotype.Repository
public interface AppCatalogueQueryRepository extends Repository<App, UUID> {

    @Query("""
        SELECT new uk.gov.justice.laa.datauserapi.contracts.dto.AppView(
            CAST(a.id AS STRING), a.name, a.description, a.url, a.enabled, a.ordinal, a.entraOid, a.entraAppId)
        FROM App a
        WHERE a.enabled = true
        ORDER BY a.ordinal, a.name, a.id
        """)
    List<AppView> findActiveApps(Pageable pageable);

    @Query("""
        SELECT new uk.gov.justice.laa.datauserapi.contracts.dto.AppView(
            CAST(a.id AS STRING), a.name, a.description, a.url, a.enabled, a.ordinal, a.entraOid, a.entraAppId)
        FROM App a
        WHERE a.enabled = true AND a.id = :appId
        """)
    Optional<AppView> findActiveAppById(@Param("appId") UUID appId);

    boolean existsByIdAndEnabledTrue(UUID appId);

    /**
     * Roles available to {@code userType}: restricted to it, or to neither/both types ({@code BOTH}); all roles when
     * {@code userType} (a {@link UserType} name) is {@code null}. {@code otherType} is the opposite type, and is
     * {@code null} with it. This is the SQL form of the INTERNAL/EXTERNAL/BOTH rule in
     * {@code UserProfileAccessQueryMapper}. It is native because HQL cannot bind an enum to the {@code text[]} column.
     */
    @Query(value = """
        SELECT ar.*
        FROM app_role ar
        JOIN app a ON a.id = ar.app_id
        WHERE a.enabled = true AND a.id = :appId
            AND (CAST(:userType AS text) IS NULL
                OR ar.user_type_restriction IS NULL
                OR COALESCE(CAST(:userType AS text) = ANY (ar.user_type_restriction), false)
                OR NOT COALESCE(CAST(:otherType AS text) = ANY (ar.user_type_restriction), false))
        ORDER BY ar.ordinal, ar.name, ar.id
        """, nativeQuery = true)
    List<AppRole> findRolesOfActiveApp(
            @Param("appId") UUID appId,
            @Param("userType") String userType,
            @Param("otherType") String otherType,
            Pageable pageable);

    @Query("""
        SELECT ar
        FROM AppRole ar
        JOIN FETCH ar.app a
        WHERE a.enabled = true AND ar.id = :appRoleId
        """)
    Optional<AppRole> findRoleOfActiveAppById(@Param("appRoleId") UUID appRoleId);
}

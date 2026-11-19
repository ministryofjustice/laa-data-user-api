package uk.gov.justice.laa.datauserapi.application.query.shared.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import uk.gov.justice.laa.datauserapi.application.query.dto.AuditAccountRow;
import uk.gov.justice.laa.datauserapi.application.query.dto.AuditProfileRow;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads for the audit user detail. Visibility is decided from the lightweight account and profile rows; only the
 * profiles that will be returned have their firm, roles and offices loaded.
 */
@org.springframework.stereotype.Repository
public interface AuditUserAccessRepository extends Repository<UserProfile, UUID> {

    @Query("""
        SELECT new uk.gov.justice.laa.datauserapi.application.query.dto.AuditAccountRow(
            u.entraOid,
            u.email,
            u.firstName,
            u.lastName,
            u.userAccountStatus,
            u.multiFirmUser,
            u.mailOnly,
            u.lastSyncedOn,
            u.createdDate,
            u.lastModified,
            u.invitationStatus
        )
        FROM EntraUser u
        WHERE u.entraOid = :userEntraObjectId
        """)
    Optional<AuditAccountRow> findAccount(@Param("userEntraObjectId") String userEntraObjectId);

    @Query("""
        SELECT new uk.gov.justice.laa.datauserapi.application.query.dto.AuditProfileRow(
            up.id,
            up.activeProfile,
            up.userType,
            f.id
        )
        FROM UserProfile up
        JOIN up.entraUser u
        LEFT JOIN up.firm f
        WHERE u.entraOid = :userEntraObjectId
        """)
    List<AuditProfileRow> findProfileRows(@Param("userEntraObjectId") String userEntraObjectId);

    @Query("""
        SELECT DISTINCT up
        FROM UserProfile up
        LEFT JOIN FETCH up.firm
        LEFT JOIN FETCH up.appRoles ar
        LEFT JOIN FETCH ar.app
        LEFT JOIN FETCH up.offices
        WHERE up.id IN :profileIds
        """)
    List<UserProfile> findProfilesWithDetails(@Param("profileIds") Collection<UUID> profileIds);
}

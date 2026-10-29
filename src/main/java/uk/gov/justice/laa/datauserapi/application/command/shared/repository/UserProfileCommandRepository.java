package uk.gov.justice.laa.datauserapi.application.command.shared.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface UserProfileCommandRepository extends JpaRepository<UserProfile, UUID> {

    @Query("""
            SELECT ups.id FROM UserProfile ups
            WHERE ups.lastCcmsSyncSuccessful = false
                 AND EXISTS (
                     SELECT candidate.id FROM UserProfile candidate
                     JOIN candidate.appRoles syncRole
                     WHERE candidate.id = ups.id AND syncRole.legacySync = true
                 )
            """)
    List<UUID> findUserProfileIdsForCcmsSync();

    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE UserProfile p
            SET p.lastCcmsSyncSuccessful = :successful,
                p.lastModifiedBy = :actorOid,
                p.lastModified = :modified
            WHERE p.id = :id
            """)
    int updateCcmsSyncResult(@Param("id") UUID id, @Param("successful") boolean successful,
                             @Param("actorOid") String actorOid, @Param("modified") LocalDateTime modified);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM UserProfile p WHERE p.entraUser.id IN :userIds ORDER BY p.id")
    List<UserProfile> findRefreshProfilesForUpdate(@Param("userIds") List<UUID> userIds);

    @Query("""
            SELECT up FROM UserProfile up
            LEFT JOIN FETCH up.firm
            WHERE up.entraUser = :entraUser
            """)
    List<UserProfile> findAllByEntraUser(EntraUser entraUser);

    @Query("SELECT DISTINCT p FROM UserProfile p LEFT JOIN FETCH p.appRoles WHERE p.entraUser.id IN :userIds")
    List<UserProfile> fetchRefreshProfileRoles(@Param("userIds") List<UUID> userIds);

    @Query("SELECT DISTINCT p FROM UserProfile p LEFT JOIN FETCH p.offices WHERE p.entraUser.id IN :userIds")
    List<UserProfile> fetchRefreshProfileOffices(@Param("userIds") List<UUID> userIds);
}

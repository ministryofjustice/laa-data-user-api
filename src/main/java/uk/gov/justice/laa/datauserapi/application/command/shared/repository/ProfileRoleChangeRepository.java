package uk.gov.justice.laa.datauserapi.application.command.shared.repository;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.entity.AppRole;

public interface ProfileRoleChangeRepository extends Repository<UserProfile, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from UserProfile p where p.id in :ids order by p.id")
    List<UserProfile> findProfilesForUpdate(Set<UUID> ids);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select r from AppRole r order by r.id")
    List<AppRole> findRolesForUpdate();

    @Modifying
    @Transactional
    @Query("update UserProfile p set p.lastCcmsSyncSuccessful = :successful where p.id = :profileId")
    int updateCcmsSyncSuccessful(@Param("profileId") UUID profileId, @Param("successful") boolean successful);
}

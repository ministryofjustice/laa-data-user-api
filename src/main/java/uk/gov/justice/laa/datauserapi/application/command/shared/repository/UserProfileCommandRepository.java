package uk.gov.justice.laa.datauserapi.application.command.shared.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserProfileCommandRepository extends JpaRepository<UserProfile, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM UserProfile p WHERE p.entraUser.id IN :userIds ORDER BY p.id")
    List<UserProfile> findRefreshProfilesForUpdate(@Param("userIds") List<UUID> userIds);

    @Query("SELECT DISTINCT p FROM UserProfile p LEFT JOIN FETCH p.appRoles WHERE p.entraUser.id IN :userIds")
    List<UserProfile> fetchRefreshProfileRoles(@Param("userIds") List<UUID> userIds);

    @Query("SELECT DISTINCT p FROM UserProfile p LEFT JOIN FETCH p.offices WHERE p.entraUser.id IN :userIds")
    List<UserProfile> fetchRefreshProfileOffices(@Param("userIds") List<UUID> userIds);
}

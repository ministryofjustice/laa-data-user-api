package uk.gov.justice.laa.datauserapi.application.command.shared.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountStatus;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EntraUserCommandRepository extends JpaRepository<EntraUser, UUID> {

    // Matches any profile linked to the firm: a multi-firm user's active profile may belong to another firm.
    @Query("""
        SELECT DISTINCT u.id
        FROM EntraUser u
        JOIN u.userProfiles p
        WHERE p.firm is not null
          AND p.firm.code = :firmCode
          AND u.userAccountStatus = :status
        ORDER BY u.id
        """)
    List<UUID> findActiveUserIdsByFirmCode(
            @Param("firmCode") String firmCode,
            @Param("status") UserAccountStatus status);

    Optional<EntraUser> findByEntraOid(String entraOid);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, UUID id);

    @Query("SELECT u.id FROM EntraUser u ORDER BY u.id")
    List<UUID> findRefreshUserIds(Pageable pageable);

    @Query("SELECT u.id FROM EntraUser u WHERE u.id > :afterId ORDER BY u.id")
    List<UUID> findRefreshUserIdsAfter(@Param("afterId") UUID afterId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM EntraUser u WHERE u.id IN :ids ORDER BY u.id")
    List<EntraUser> findRefreshUsersForUpdate(@Param("ids") List<UUID> ids);
}

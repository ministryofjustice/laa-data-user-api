package uk.gov.justice.laa.datauserapi.application.command.shared.repository;

import org.springframework.data.jpa.repository.JpaRepository;
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

    @Query("""
        SELECT DISTINCT u.id
        FROM EntraUser u
        JOIN u.userProfiles p
        WHERE p.firm is not null
          AND p.firm.id = :firmId
          AND p.activeProfile = true
          AND u.userAccountStatus = :status
          AND u.multiFirmUser = false
        ORDER BY u.id
        """)
    List<UUID> findActiveSingleFirmUserIdsByFirmId(
            @Param("firmId") UUID firmId,
            @Param("status") UserAccountStatus status);

    Optional<EntraUser> findByEntraOid(String entraOid);
}

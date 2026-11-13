package uk.gov.justice.laa.datauserapi.application.command.shared.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.datauserapi.entity.Firm;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FirmCommandRepository extends JpaRepository<Firm, UUID> {

    boolean existsByIdAndEnabledTrue(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM Firm f WHERE f.id = :id AND f.enabled = true")
    Optional<Firm> findEnabledByIdForUpdate(@Param("id") UUID id);

    @Query("SELECT COUNT(f) > 0 FROM Firm f WHERE f.code = :code")
    boolean existsByCode(@Param("code") String code);

}

package uk.gov.justice.laa.datauserapi.application.command.shared.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.datauserapi.entity.Firm;

import java.util.UUID;

@Repository
public interface FirmCommandRepository extends JpaRepository<Firm, UUID> {

    @Query("SELECT COUNT(f) > 0 FROM Firm f WHERE f.code = :code")
    boolean existsByCode(@Param("code") String code);

}

package uk.gov.justice.laa.datauserapi.application.command.shared.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.datauserapi.entity.Office;

import java.util.UUID;

@Repository
public interface OfficeCommandRepository extends JpaRepository<Office, UUID> {
}

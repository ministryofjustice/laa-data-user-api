package uk.gov.justice.laa.datauserapi.application.query.shared.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;

import java.util.List;
import java.util.UUID;

@Repository
public interface EntraUserQueryRepository extends JpaRepository<EntraUser, UUID> {
    List<EntraUser> findByFirmId(String firmId);
}

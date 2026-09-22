package uk.gov.justice.laa.datauserapi.application.command.shared.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.datauserapi.entity.DisableUserReasonLookup;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DisableUserReasonRepository extends JpaRepository<DisableUserReasonLookup, UUID> {

    Optional<DisableUserReasonLookup> findByName(String name);

    Optional<DisableUserReasonLookup> findFirstByEntraDescription(String entraDescription);

}

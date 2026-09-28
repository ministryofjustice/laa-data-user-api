package uk.gov.justice.laa.datauserapi.application.command.shared.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.datauserapi.entity.DeactivateUserReasonLookup;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeactivateUserReasonRepository extends JpaRepository<DeactivateUserReasonLookup, UUID> {

    Optional<DeactivateUserReasonLookup> findByName(String name);

    Optional<DeactivateUserReasonLookup> findFirstByEntraDescription(String entraDescription);

}

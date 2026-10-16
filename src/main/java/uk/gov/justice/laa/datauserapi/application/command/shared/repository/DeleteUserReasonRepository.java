package uk.gov.justice.laa.datauserapi.application.command.shared.repository;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.datauserapi.entity.DeleteUserReasonEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeleteUserReasonRepository extends JpaRepository<DeleteUserReasonEntity, UUID> {

    Optional<DeleteUserReasonEntity> findByCode(@NotBlank(message = "Reason code must be provided") @Size(min = 1, max = 255, message = "Reason code must be between 1 and 255 characters") String code);
}

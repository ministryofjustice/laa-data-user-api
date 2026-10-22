package uk.gov.justice.laa.datauserapi.application.command.shared.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;

import java.util.UUID;

@Repository
public interface UserProfileCommandRepository extends JpaRepository<UserProfile, UUID> {
}

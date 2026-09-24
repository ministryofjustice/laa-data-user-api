package uk.gov.justice.laa.datauserapi.application.command.shared.repository;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.datauserapi.entity.UserAccountStatusAudit;

@Repository
public interface UserAccountStatusAuditRepository extends JpaRepository<UserAccountStatusAudit, Long> {}

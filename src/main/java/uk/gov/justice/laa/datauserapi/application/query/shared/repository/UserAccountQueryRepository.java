package uk.gov.justice.laa.datauserapi.application.query.shared.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.datauserapi.application.query.dto.AccountStatusHistoryView;
import uk.gov.justice.laa.datauserapi.entity.UserAccountStatusAudit;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserAccountQueryRepository extends JpaRepository<UserAccountStatusAudit, Long> {

    @Query("""
        SELECT new uk.gov.justice.laa.datauserapi.application.query.dto.AccountStatusHistoryView(
            a.entraUser.entraOid,
            a.userAccountStatus,
            a.statusChangedDate,
            a.statusChangedBy,
            a.disableUserReasonLookup.name
        )
        FROM UserAccountStatusAudit a
        WHERE a.entraUser.entraOid = :userEntraObjectId
        ORDER BY a.statusChangedDate DESC
    """)
    List<AccountStatusHistoryView> findAuditHistoryByUserEntraObjectId(@Param("userEntraObjectId") UUID userEntraObjectId);
}

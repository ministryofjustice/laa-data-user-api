package uk.gov.justice.laa.datauserapi.application.query.shared.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import uk.gov.justice.laa.datauserapi.application.query.dto.AccountStatusHistoryView;
import uk.gov.justice.laa.datauserapi.entity.UserAccountStatusAudit;

import java.util.List;

@org.springframework.stereotype.Repository
public interface UserAccountQueryRepository extends Repository<UserAccountStatusAudit, Long> {

    @Query("""
        SELECT new uk.gov.justice.laa.datauserapi.application.query.dto.AccountStatusHistoryView(
            eu.id,
            eu.entraOid,
            a.userAccountStatus,
            a.statusChangedDate,
            a.statusChangedBy,
            a.deactivateUserReasonLookup.name
        )
        FROM UserAccountStatusAudit a
        JOIN EntraUser eu ON a.entraUserId = eu.id
        WHERE eu.entraOid = :userEntraObjectId
        ORDER BY a.statusChangedDate DESC
        """)
    List<AccountStatusHistoryView> findAuditHistoryByUserEntraObjectId(@Param("userEntraObjectId") String userEntraObjectId);
}

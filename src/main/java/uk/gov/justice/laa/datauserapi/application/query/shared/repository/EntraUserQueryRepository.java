package uk.gov.justice.laa.datauserapi.application.query.shared.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import uk.gov.justice.laa.datauserapi.application.query.dto.UserAccountStatusView;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;

import java.util.Optional;
import java.util.UUID;

@org.springframework.stereotype.Repository
public interface EntraUserQueryRepository extends Repository<EntraUser, UUID> {

    @Query("""
        SELECT new uk.gov.justice.laa.datauserapi.application.query.dto.UserAccountStatusView(
            u.entraOid,
            u.email,
            u.userAccountStatus,
            u.lastModified
        )
        FROM EntraUser u
        WHERE u.id = :userEntraObjectId
        """)
    Optional<UserAccountStatusView> findStatusByUserEntraObjectId(
            @Param("userEntraObjectId") UUID userEntraObjectId);
}

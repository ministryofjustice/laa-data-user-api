package uk.gov.justice.laa.datauserapi.application.query.shared.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import uk.gov.justice.laa.datauserapi.contracts.dto.RoleAssignmentRestrictionView;
import uk.gov.justice.laa.datauserapi.entity.RoleAssignment;
import uk.gov.justice.laa.datauserapi.entity.RoleAssignmentId;

/**
 * Reads which roles may assign which other roles. A restriction is eligible only when the applications of both the
 * assignable role (the role being assigned) and the assigning role are active. The application fields and the
 * {@code appId} filter describe the assignable role, not the assigning role.
 */
@org.springframework.stereotype.Repository
public interface RoleAssignmentRestrictionQueryRepository extends Repository<RoleAssignment, RoleAssignmentId> {

    @Query(value = """
        SELECT new uk.gov.justice.laa.datauserapi.contracts.dto.RoleAssignmentRestrictionView(
            CAST(assignable.id AS STRING), assignable.name, CAST(a.id AS STRING), a.entraAppId, a.name,
            CAST(assigning.id AS STRING), assigning.name)
        FROM RoleAssignment ra
        JOIN ra.assignableRole assignable
        JOIN assignable.app a
        JOIN ra.assigningRole assigning
        JOIN assigning.app assigningApp
        WHERE a.enabled = true AND assigningApp.enabled = true
            AND (:appId IS NULL OR a.id = :appId)
        ORDER BY a.ordinal, a.name, a.id, assignable.ordinal, assignable.name, assignable.id,
            assigning.ordinal, assigning.name, assigning.id
        """,
        countQuery = """
        SELECT COUNT(*)
        FROM RoleAssignment ra
        JOIN ra.assignableRole assignable
        JOIN assignable.app a
        JOIN ra.assigningRole assigning
        JOIN assigning.app assigningApp
        WHERE a.enabled = true AND assigningApp.enabled = true
            AND (:appId IS NULL OR a.id = :appId)
        """)
    Page<RoleAssignmentRestrictionView> findEligibleRestrictions(@Param("appId") UUID appId, Pageable pageable);
}

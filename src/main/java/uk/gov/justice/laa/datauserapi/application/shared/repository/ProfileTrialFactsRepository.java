package uk.gov.justice.laa.datauserapi.application.shared.repository;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import uk.gov.justice.laa.datauserapi.entity.AppRole;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;

/** Read-only authorisation facts shared by the two trial sides, never a write repository. */
public interface ProfileTrialFactsRepository extends Repository<UserProfile, UUID> {
    @Query("""
            select distinct p from UserProfile p
            join fetch p.entraUser left join fetch p.firm left join fetch p.appRoles r left join fetch r.app
            where p.id in :ids
            """)
    List<UserProfile> findProfiles(Set<UUID> ids);

    @Query("select distinct r from AppRole r join fetch r.app left join fetch r.permissions")
    List<AppRole> findRoleCatalogue();

    @Query("""
            select new uk.gov.justice.laa.datauserapi.application.shared.repository.ProfileTrialFactsRepository$Restriction(
                a.assignableRole.id, a.assigningRole.id)
            from RoleAssignment a
            """)
    List<Restriction> findRestrictions();

    record Restriction(UUID roleId, UUID assigningRoleId) {
    }
}

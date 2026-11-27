package uk.gov.justice.laa.datauserapi.application.query.shared.repository;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;

public interface ProfileTrialQueryRepository extends Repository<UserProfile, UUID> {
    Page<UserProfile> findAll(Specification<UserProfile> specification, Pageable pageable);

    @Query("""
            select distinct p from UserProfile p join fetch p.entraUser left join fetch p.firm
            left join fetch p.appRoles r left join fetch r.app where p.id in :ids
            """)
    List<UserProfile> findDetails(Set<UUID> ids);

    @Query("select distinct p from UserProfile p left join fetch p.offices o left join fetch o.firm where p.id in :ids")
    List<UserProfile> findOffices(Set<UUID> ids);
}

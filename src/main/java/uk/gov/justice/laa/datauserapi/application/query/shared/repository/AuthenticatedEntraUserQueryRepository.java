package uk.gov.justice.laa.datauserapi.application.query.shared.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.Repository;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;

import java.util.Optional;
import java.util.UUID;

public interface AuthenticatedEntraUserQueryRepository extends Repository<EntraUser, UUID> {

    @EntityGraph(attributePaths = {
        "userProfiles.appRoles.app",
        "userProfiles.appRoles.permissions",
        "userProfiles.firm",
        "userProfiles.offices"
    })
    Optional<EntraUser> findByEntraOid(String entraOid);

}

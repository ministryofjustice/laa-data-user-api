package uk.gov.justice.laa.datauserapi.application.query.shared.repository;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.model.Permission;
import uk.gov.justice.laa.datauserapi.model.UserType;

@org.springframework.stereotype.Repository
public interface FirmDirectoryActorQueryRepository extends Repository<EntraUser, UUID> {

    @Query("""
        SELECT profile.userType
        FROM EntraUser actor
        JOIN actor.userProfiles profile
        WHERE actor.entraOid = :entraOid
            AND profile.activeProfile = true
        """)
    Optional<UserType> findActiveUserType(@Param("entraOid") String entraOid);

    @Query("""
        SELECT DISTINCT permission
        FROM EntraUser actor
        JOIN actor.userProfiles profile
        JOIN profile.appRoles appRole
        JOIN appRole.permissions permission
        WHERE actor.entraOid = :entraOid
            AND profile.activeProfile = true
        """)
    Set<Permission> findActivePermissions(@Param("entraOid") String entraOid);

    @Query("""
        SELECT profile.firm.code
        FROM EntraUser actor
        JOIN actor.userProfiles profile
        WHERE actor.entraOid = :entraOid
            AND profile.activeProfile = true
        """)
    Optional<String> findActiveFirmCode(@Param("entraOid") String entraOid);
}

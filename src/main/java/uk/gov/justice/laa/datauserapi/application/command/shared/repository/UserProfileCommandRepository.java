package uk.gov.justice.laa.datauserapi.application.command.shared.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserProfileCommandRepository extends JpaRepository<UserProfile, UUID> {

    @Query("""
            SELECT ups FROM UserProfile ups
                        JOIN FETCH ups.entraUser eu
                        JOIN FETCH ups.firm f
                        JOIN FETCH ups.appRoles ars
            WHERE ups.lastCcmsSyncSuccessful = false
                AND ars.legacySync = true
            """)
    List<UserProfile> findUserProfilesForCcmsSync();

    @Query("""
            SELECT up FROM UserProfile up
            LEFT JOIN FETCH up.firm
            WHERE up.entraUser = :entraUser
            """)
    List<UserProfile> findAllByEntraUser(EntraUser entraUser);
}

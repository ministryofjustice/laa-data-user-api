package uk.gov.justice.laa.datauserapi.application.command.shared.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.datauserapi.application.command.dto.UserAccountCreationData;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserCreationRepository extends JpaRepository<EntraUser, UUID> {
    default Optional<UserAccountCreationData> findExisting(String email) {
        return findExistingByEmail(email, Pageable.ofSize(1)).stream().findFirst();
    }

    @Query("""
                select new uk.gov.justice.laa.datauserapi.application.command.dto.UserAccountCreationData(
                    eu.entraOid, up.id)
                from EntraUser eu left join eu.userProfiles up
                where lower(eu.email) = lower(:email)
                order by case when up.activeProfile = true then 0 else 1 end, up.id
                """)
    List<UserAccountCreationData> findExistingByEmail(@Param("email") String email, Pageable pageable);

    default Optional<UserAccountCreationData> findExistingByEntraOid(UUID entraOid) {
        return findExistingByEntraOid(entraOid.toString(), Pageable.ofSize(1)).stream().findFirst();
    }

    @Query("""
                select new uk.gov.justice.laa.datauserapi.application.command.dto.UserAccountCreationData(
                    eu.entraOid, up.id)
                from EntraUser eu left join eu.userProfiles up
                where lower(eu.entraOid) = lower(:entraOid)
                order by case when up.activeProfile = true then 0 else 1 end, up.id
                """)
    List<UserAccountCreationData> findExistingByEntraOid(@Param("entraOid") String entraOid, Pageable pageable);

    default void lockEmail(String email) {
        // Serialize local creation for this email without requiring a shared-schema migration.
        acquireEmailLock(email.strip().toLowerCase(Locale.ROOT));
    }

    @Query(value = "select pg_advisory_xact_lock(hashtextextended(:email, 0))", nativeQuery = true)
    void acquireEmailLock(@Param("email") String email);
}

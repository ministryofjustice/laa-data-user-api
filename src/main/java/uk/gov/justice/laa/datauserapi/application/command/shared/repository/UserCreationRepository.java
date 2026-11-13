package uk.gov.justice.laa.datauserapi.application.command.shared.repository;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.datauserapi.application.command.dto.UserAccountCreationData;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Repository
public class UserCreationRepository {
    private final EntityManager entityManager;

    public UserCreationRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public Optional<UserAccountCreationData> findExisting(String email) {
        return entityManager.createQuery("""
                select new uk.gov.justice.laa.datauserapi.application.command.dto.UserAccountCreationData(
                    eu.entraOid, up.id)
                from EntraUser eu left join eu.userProfiles up
                where lower(eu.email) = lower(:email)
                order by case when up.activeProfile = true then 0 else 1 end, up.id
                """, UserAccountCreationData.class)
                .setParameter("email", email)
                .setMaxResults(1)
                .getResultList().stream().findFirst();
    }

    public Optional<UserAccountCreationData> findExistingByEntraOid(UUID entraOid) {
        return entityManager.createQuery("""
                select new uk.gov.justice.laa.datauserapi.application.command.dto.UserAccountCreationData(
                    eu.entraOid, up.id)
                from EntraUser eu left join eu.userProfiles up
                where lower(eu.entraOid) = lower(:entraOid)
                order by case when up.activeProfile = true then 0 else 1 end, up.id
                """, UserAccountCreationData.class)
                .setParameter("entraOid", entraOid.toString())
                .setMaxResults(1)
                .getResultList().stream().findFirst();
    }

    public void lockEmail(String email) {
        // Serialize local creation for this email without requiring a shared-schema migration.
        entityManager.createNativeQuery("select pg_advisory_xact_lock(hashtextextended(:email, 0))")
                .setParameter("email", email.strip().toLowerCase(Locale.ROOT)).getSingleResult();
    }
}

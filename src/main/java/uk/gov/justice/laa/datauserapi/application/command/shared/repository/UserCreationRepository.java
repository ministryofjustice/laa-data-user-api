package uk.gov.justice.laa.datauserapi.application.command.shared.repository;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.datauserapi.application.command.dto.UserAccountCreationData;

import java.util.Optional;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Repository
public class UserCreationRepository {
    private final EntityManager entityManager;

    public UserCreationRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public Optional<UserAccountCreationData> findExisting(String email) {
        return findExistingByPredicate("lower(eu.email) = lower(:lookupValue)", email);
    }

    public Optional<UserAccountCreationData> findExistingByEntraOid(UUID entraOid) {
        return findExistingByPredicate("eu.entra_oid = :lookupValue", entraOid.toString());
    }

    private Optional<UserAccountCreationData> findExistingByPredicate(String predicate, String lookupValue) {
        List<?> rows = entityManager.createNativeQuery("""
                select eu.entra_oid, up.id as profile_id
                from entra_user eu left join user_profile up on up.entra_user_id = eu.id
                where %s
                order by up.active_profile desc nulls last, up.id
                limit 1
                """.formatted(predicate)).setParameter("lookupValue", lookupValue).getResultList();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Object[] row = (Object[]) rows.getFirst();
        return Optional.of(new UserAccountCreationData((String) row[0], (UUID) row[1]));
    }

    public void lockEmail(String email) {
        // Serialize local creation for this email without requiring a shared-schema migration.
        entityManager.createNativeQuery("select pg_advisory_xact_lock(hashtextextended(:email, 0))")
                .setParameter("email", email.strip().toLowerCase(Locale.ROOT)).getSingleResult();
    }

}

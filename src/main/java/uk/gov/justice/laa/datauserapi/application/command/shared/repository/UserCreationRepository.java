package uk.gov.justice.laa.datauserapi.application.command.shared.repository;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.CreateUserAccountCommand;
import uk.gov.justice.laa.datauserapi.contracts.response.CreateUserAccountResponse;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

@Repository
public class UserCreationRepository {
    private final EntityManager entityManager;

    public UserCreationRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public boolean firmExists(UUID firmId) {
        return Boolean.TRUE.equals(entityManager.createNativeQuery(
                "select exists (select 1 from firm where id = :firmId and enabled = true)", Boolean.class)
                .setParameter("firmId", firmId).getSingleResult());
    }

    public Optional<CreateUserAccountResponse> findExisting(String email) {
        List<?> rows = entityManager.createNativeQuery("""
                select eu.entra_oid, up.id as profile_id
                from entra_user eu left join user_profile up on up.entra_user_id = eu.id
                where lower(eu.email) = lower(:email)
                order by up.active_profile desc nulls last, up.id
                limit 1
                """).setParameter("email", email).getResultList();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Object[] row = (Object[]) rows.getFirst();
        return Optional.of(new CreateUserAccountResponse(false, "User already exists",
                (String) row[0], (UUID) row[1]));
    }

    public void lockEmail(String email) {
        // Serialize local creation for this email without requiring a shared-schema migration.
        entityManager.createNativeQuery("select pg_advisory_xact_lock(hashtextextended(:email, 0))")
                .setParameter("email", email).getSingleResult();
    }

    public CreateUserAccountResponse insert(CreateUserAccountCommand command, UUID entraOid, String actorOid) {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        entityManager.createNativeQuery("""
                insert into entra_user
                (id, created_date, created_by, email, entra_oid, first_name, last_name,
                 status, multi_firm_user, enabled, mail_only, ccms_ebs_user)
                values (:id, current_timestamp, :actor, :email, :oid, :firstName, :lastName,
                        'ACTIVATION_REQUIRED', :multiFirm, true, false, false)
                """).setParameter("id", userId).setParameter("actor", actorOid)
                .setParameter("email", command.email()).setParameter("oid", entraOid.toString())
                .setParameter("firstName", command.firstName()).setParameter("lastName", command.lastName())
                .setParameter("multiFirm", command.multiFirmUser()).executeUpdate();
        entityManager.createNativeQuery("""
                insert into user_profile
                (id, created_date, created_by, active_profile, entra_user_id, firm_id, legacy_user_id,
                 status, user_type, silas_status, last_sync_successful, unrestricted_office_access)
                values (:id, current_timestamp, :actor, true, :userId, :firmId, :legacyId, 'PENDING', 'EXTERNAL',
                        'ACTIVATION_REQUIRED', false, false)
                """).setParameter("id", profileId).setParameter("actor", actorOid)
                .setParameter("userId", userId).setParameter("firmId", UUID.fromString(command.firmId()))
                .setParameter("legacyId", UUID.randomUUID()).executeUpdate();
        return new CreateUserAccountResponse(true, "User created", entraOid.toString(), profileId);
    }
}

package uk.gov.justice.laa.datauserapi.application.command.shared.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.CreateUserAccountCommand;
import uk.gov.justice.laa.datauserapi.contracts.response.CreateUserAccountResponse;

import java.util.Optional;
import java.util.UUID;

@Repository
public class UserCreationRepository {
    private final JdbcTemplate jdbc;

    public UserCreationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean firmExists(UUID firmId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists (select 1 from firm where id = ? and enabled = true)", Boolean.class, firmId));
    }

    public Optional<CreateUserAccountResponse> findExisting(String email) {
        return jdbc.query("""
                select eu.entra_oid, up.id as profile_id
                from entra_user eu left join user_profile up on up.entra_user_id = eu.id
                where lower(eu.email) = lower(?)
                order by up.active_profile desc nulls last, up.id
                limit 1
                """, (rs, row) -> new CreateUserAccountResponse(false, "User already exists",
                rs.getString("entra_oid"), rs.getObject("profile_id", UUID.class)), email).stream().findFirst();
    }

    public void lockEmail(String email) {
        // Serialize local creation for this email without requiring a shared-schema migration.
        jdbc.query("select pg_advisory_xact_lock(hashtextextended(?, 0))",
                rs -> { }, email);
    }

    public CreateUserAccountResponse insert(CreateUserAccountCommand command, UUID entraOid, String actorOid) {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        jdbc.update("""
                insert into entra_user
                (id, created_date, created_by, email, entra_oid, first_name, last_name,
                 status, multi_firm_user, enabled, mail_only, ccms_ebs_user)
                values (?, current_timestamp, ?, ?, ?, ?, ?, 'ACTIVATION_REQUIRED', ?, true, false, false)
                """, userId, actorOid, command.email(), entraOid.toString(), command.firstName(),
                command.lastName(), command.multiFirmUser());
        jdbc.update("""
                insert into user_profile
                (id, created_date, created_by, active_profile, entra_user_id, firm_id, legacy_user_id,
                 status, user_type, silas_status, last_sync_successful, unrestricted_office_access)
                values (?, current_timestamp, ?, true, ?, ?, ?, 'PENDING', 'EXTERNAL',
                        'ACTIVATION_REQUIRED', false, false)
                """, profileId, actorOid, userId, UUID.fromString(command.firmId()), UUID.randomUUID());
        return new CreateUserAccountResponse(true, "User created", entraOid.toString(), profileId);
    }
}

package uk.gov.justice.laa.datauserapi.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.command.CreateUserAccountCommand;
import uk.gov.justice.laa.datauserapi.command.CreateUserAccountResult;
import uk.gov.justice.laa.datauserapi.exception.CreateUserException;

import java.util.UUID;

@Slf4j
@Service
public class UserAccountCreationService {
    private final JdbcTemplate jdbcTemplate;
    private final EntraUserClient entraUserClient;

    public UserAccountCreationService(JdbcTemplate jdbcTemplate, EntraUserClient entraUserClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.entraUserClient = entraUserClient;
    }

    @Transactional
    public CreateUserAccountResult create(CreateUserAccountCommand command, String actorId) {
        Integer duplicate = jdbcTemplate.queryForObject(
                "select count(*) from entra_user where lower(email) = lower(?)",
                Integer.class, command.email());
        if (duplicate != null && duplicate > 0) {
            throw new CreateUserException(HttpStatus.CONFLICT,
                    "A user with this email already exists");
        }

        UUID firmId;
        try {
            firmId = UUID.fromString(command.firmId());
        } catch (IllegalArgumentException ex) {
            throw new CreateUserException(HttpStatus.BAD_REQUEST, "firmId must be a valid UUID");
        }
        Integer firmCount = jdbcTemplate.queryForObject(
                "select count(*) from firm where id = ? and enabled = true", Integer.class, firmId);
        if (firmCount == null || firmCount == 0) {
            throw new CreateUserException(HttpStatus.BAD_REQUEST, "firmId does not identify an active firm");
        }

        String entraOid = entraUserClient.createUser(command);
        UUID entraUserId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        try {
            jdbcTemplate.update("""
                    insert into entra_user
                    (id, created_date, created_by, email, entra_oid, first_name, last_name,
                     status, multi_firm_user, enabled, mail_only, ccms_ebs_user)
                    values (?, current_timestamp, ?, ?, ?, ?, ?, 'ACTIVATION_REQUIRED', ?, true, false, false)
                    """,
                    entraUserId, actorId, command.email(), entraOid, command.firstName(),
                    command.lastName(), command.multiFirmUser());
            jdbcTemplate.update("""
                    insert into user_profile
                    (id, created_date, created_by, active_profile, entra_user_id, firm_id,
                     status, user_type, silas_status, last_sync_successful, unrestricted_office_access)
                    values (?, current_timestamp, ?, true, ?, ?, 'PENDING', 'EXTERNAL',
                            'ACTIVATION_REQUIRED', false, false)
                    """,
                    profileId, actorId, entraUserId, firmId);
        } catch (DataAccessException ex) {
            log.error("Create user local persistence failed; orphaned Entra identity requires reconciliation",
                    ex);
            throw new CreateUserException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "The user could not be stored locally", ex);
        }
        return new CreateUserAccountResult(true, "User created", entraOid, profileId);
    }
}

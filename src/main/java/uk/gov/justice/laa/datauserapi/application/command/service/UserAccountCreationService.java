package uk.gov.justice.laa.datauserapi.application.command.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import uk.gov.justice.laa.datauserapi.application.command.mapper.UserAccountCreationMapper;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.FirmCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.UserCreationRepository;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.CreateUserAccountCommand;
import uk.gov.justice.laa.datauserapi.client.ts.TechServicesClient;
import uk.gov.justice.laa.datauserapi.contracts.response.CreateUserAccountResponse;
import uk.gov.justice.laa.datauserapi.exception.CreateUserException;
import uk.gov.justice.laa.datauserapi.exception.UserAlreadyExistsException;

import java.util.UUID;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Slf4j
@Service
public class UserAccountCreationService {
    private final UserCreationRepository repository;
    private final FirmCommandRepository firmRepository;
    private final TechServicesClient client;
    private final UserAccountPersistenceService persistence;
    private final UserAccountCreationMapper mapper;

    public UserAccountCreationService(UserCreationRepository repository, FirmCommandRepository firmRepository,
                                      TechServicesClient client,
                                      UserAccountPersistenceService persistence, UserAccountCreationMapper mapper) {
        this.repository = repository;
        this.firmRepository = firmRepository;
        this.client = client;
        this.persistence = persistence;
        this.mapper = mapper;
    }

    public CreateUserAccountResponse create(CreateUserAccountCommand command, String actorOid) {
        repository.findExisting(command.email()).map(mapper::toExistingResponse)
                .ifPresent(existing -> {
                    throw new UserAlreadyExistsException(existing);
                });
        UUID firmId = UUID.fromString(command.firmId());
        if (!firmRepository.existsByIdAndEnabledTrue(firmId)) {
            throw new CreateUserException(CreateUserException.Reason.INVALID_REQUEST,
                    HttpStatus.BAD_REQUEST, "firmId does not identify an active firm", "firmId");
        }

        TechServicesClient.Identity identity = client.createUser(
                new TechServicesClient.Registration(command.email(), command.firstName(), command.lastName()));
        if (!identity.created()) {
            log.info("Existing identity detected", kv("event", "existing_identity_detected"),
                    kv("step", "identity_registration"), kv("system", "TechServices/Entra"), kv("identityId", identity.id()));
            repository.findExistingByEntraOid(identity.id()).map(mapper::toExistingResponse)
                    .ifPresent(existing -> {
                        throw new UserAlreadyExistsException(existing);
                    });
        }
        try {
            // The downstream operation is intentionally outside the local transaction and is never compensated.
            CreateUserAccountResponse result = persistence.create(command, identity.id(), actorOid);
            log.info("User created", kv("event", "user_created"), kv("step", "local_commit"),
                    kv("system", "PostgreSQL"), kv("identityId", identity.id()), kv("profileId", result.userProfileId()));
            return result;
        } catch (UserAlreadyExistsException ex) {
            logRetainedIdentity(identity.id(), "local_conflict");
            throw ex;
        } catch (CreateUserException ex) {
            if (ex.reason() == CreateUserException.Reason.INVALID_REQUEST) {
                logRetainedIdentity(identity.id(), "firm_inactive_before_local_commit");
            }
            throw ex;
        } catch (DataIntegrityViolationException ex) {
            logRetainedIdentity(identity.id(), "local_constraint");
            var existing = repository.findExisting(command.email())
                    .or(() -> repository.findExistingByEntraOid(identity.id()))
                    .map(mapper::toExistingResponse);
            if (existing.isPresent()) {
                throw new UserAlreadyExistsException(existing.get());
            }
            throw new CreateUserException(CreateUserException.Reason.LOCAL_PERSISTENCE_FAILURE,
                    HttpStatus.INTERNAL_SERVER_ERROR, "The user could not be stored locally", ex);
        } catch (DataAccessException | TransactionException ex) {
            logRetainedIdentity(identity.id(), "local_persistence_or_commit");
            throw new CreateUserException(CreateUserException.Reason.LOCAL_PERSISTENCE_FAILURE,
                    HttpStatus.INTERNAL_SERVER_ERROR, "The user could not be stored locally", ex);
        }
    }

    private void logRetainedIdentity(UUID identityId, String step) {
        log.error("Identity retained; manual reconciliation required",
                kv("event", "identity_reconciliation_required"),
                kv("step", step), kv("system", "PostgreSQL"), kv("identityId", identityId));
    }
}

package uk.gov.justice.laa.datauserapi.application.command.service;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
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
            throw new CreateUserException(HttpStatus.BAD_REQUEST, "firmId does not identify an active firm", "firmId");
        }

        final TechServicesClient.Identity identity;
        try {
            identity = client.createUser(new TechServicesClient.Registration(command.email(), command.firstName(), command.lastName()));
        } catch (CreateUserException ex) {
            if (ex.status().is5xxServerError()) {
                log.error("User creation failed correlationId={} step=identity_registration system=TechServices/Entra status={}",
                        MDC.get("correlationId"), ex.status().value());
            } else {
                log.warn("User creation rejected correlationId={} step=identity_registration system=TechServices/Entra status={}",
                        MDC.get("correlationId"), ex.status().value());
            }
            throw ex;
        }
        if (!identity.created()) {
            log.info("Existing identity detected correlationId={} step=identity_registration system=TechServices/Entra identityId={}",
                    MDC.get("correlationId"), identity.id());
            repository.findExistingByEntraOid(identity.id()).map(mapper::toExistingResponse)
                    .ifPresent(existing -> {
                        throw new UserAlreadyExistsException(existing);
                    });
        }
        try {
            // The downstream operation is intentionally outside the local transaction and is never compensated.
            CreateUserAccountResponse result = persistence.create(command, identity.id(), actorOid);
            log.info("User created correlationId={} step=local_commit system=PostgreSQL identityId={} profileId={}",
                    MDC.get("correlationId"), identity.id(), result.userProfileId());
            return result;
        } catch (UserAlreadyExistsException ex) {
            logRetainedIdentity(identity.id(), "local_conflict");
            throw ex;
        } catch (DataIntegrityViolationException ex) {
            logRetainedIdentity(identity.id(), "local_constraint");
            var existing = repository.findExisting(command.email())
                    .or(() -> repository.findExistingByEntraOid(identity.id()))
                    .map(mapper::toExistingResponse);
            if (existing.isPresent()) {
                throw new UserAlreadyExistsException(existing.get());
            }
            throw new CreateUserException(HttpStatus.INTERNAL_SERVER_ERROR, "The user could not be stored locally", ex);
        } catch (DataAccessException | TransactionException ex) {
            logRetainedIdentity(identity.id(), "local_persistence_or_commit");
            throw new CreateUserException(HttpStatus.INTERNAL_SERVER_ERROR, "The user could not be stored locally", ex);
        }
    }

    private void logRetainedIdentity(UUID identityId, String step) {
        log.error("Identity retained; manual reconciliation required correlationId={} step={} system=PostgreSQL identityId={}",
                MDC.get("correlationId"), step, identityId);
    }
}

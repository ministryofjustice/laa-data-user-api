package uk.gov.justice.laa.datauserapi.application.command.service;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.UserCreationRepository;
import uk.gov.justice.laa.datauserapi.client.ts.EntraUserClient;
import uk.gov.justice.laa.datauserapi.contracts.request.CreateUserAccountCommand;
import uk.gov.justice.laa.datauserapi.contracts.response.CreateUserAccountResult;
import uk.gov.justice.laa.datauserapi.exception.CreateUserException;
import uk.gov.justice.laa.datauserapi.exception.UserAlreadyExistsException;

import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
public class UserAccountCreationService {
    private final UserCreationRepository repository;
    private final EntraUserClient client;
    private final TransactionTemplate transaction;

    public UserAccountCreationService(UserCreationRepository repository, EntraUserClient client,
                                      PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.client = client;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public CreateUserAccountResult create(CreateUserAccountCommand request, String actorOid) {
        if (actorOid == null || actorOid.isBlank() || !repository.canCreateExternalUser(actorOid)) {
            log.warn("User creation denied correlationId={} step=actor_authorization", MDC.get("correlationId"));
            throw new AccessDeniedException("An internal actor with CREATE_EXTERNAL_USER permission is required");
        }
        CreateUserAccountCommand command = new CreateUserAccountCommand(request.email().strip().toLowerCase(Locale.ROOT),
                request.firstName(), request.lastName(), request.userType(), request.multiFirmUser(), request.firmId());
        repository.findExisting(command.email()).ifPresent(existing -> {
            throw new UserAlreadyExistsException(existing);
        });
        if (!repository.firmExists(UUID.fromString(command.firmId()))) {
            throw new CreateUserException(HttpStatus.BAD_REQUEST, "firmId does not identify an active firm", "firmId");
        }

        final EntraUserClient.Identity identity;
        try {
            identity = client.createUser(command);
        } catch (CreateUserException ex) {
            log.error("User creation failed correlationId={} step=identity_registration system=TechServices/Entra status={}",
                    MDC.get("correlationId"), ex.status().value());
            throw ex;
        }
        if (!identity.created()) {
            log.info("Existing identity detected correlationId={} step=identity_registration system=TechServices/Entra identityId={}",
                    MDC.get("correlationId"), identity.id());
            CreateUserAccountResult existing = repository.findExisting(command.email()).orElse(
                    new CreateUserAccountResult(false, "Identity already exists; no local profile was created",
                            identity.id().toString(), null));
            throw new UserAlreadyExistsException(existing);
        }
        try {
            // The downstream operation is intentionally outside the local transaction and is never compensated.
            CreateUserAccountResult result = transaction.execute(status -> {
                repository.lockEmail(command.email());
                repository.findExisting(command.email()).ifPresent(existing -> {
                    throw new UserAlreadyExistsException(existing);
                });
                return repository.insert(command, identity.id(), actorOid);
            });
            log.info("User created correlationId={} step=local_commit system=PostgreSQL identityId={} profileId={}",
                    MDC.get("correlationId"), identity.id(), result.userProfileId());
            return result;
        } catch (UserAlreadyExistsException ex) {
            logRetainedIdentity(identity.id(), "local_conflict");
            throw ex;
        } catch (DataIntegrityViolationException ex) {
            logRetainedIdentity(identity.id(), "local_constraint");
            var existing = repository.findExisting(command.email());
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

package uk.gov.justice.laa.datauserapi.application.command.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.UserCreationRepository;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.CreateUserAccountCommand;
import uk.gov.justice.laa.datauserapi.contracts.response.CreateUserAccountResponse;
import uk.gov.justice.laa.datauserapi.exception.UserAlreadyExistsException;

import java.util.UUID;

@Service
public class UserAccountPersistenceService {
    private final UserCreationRepository repository;

    public UserAccountPersistenceService(UserCreationRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public CreateUserAccountResponse create(CreateUserAccountCommand command, UUID identityId, String actorOid) {
        repository.lockEmail(command.email());
        repository.findExisting(command.email()).ifPresent(existing -> {
            throw new UserAlreadyExistsException(existing);
        });
        return repository.insert(command, identityId, actorOid);
    }
}

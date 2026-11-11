package uk.gov.justice.laa.datauserapi.application.command.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.command.dto.UserAccountCreationData;
import uk.gov.justice.laa.datauserapi.application.command.mapper.UserAccountCreationMapper;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.UserCreationRepository;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.CreateUserAccountCommand;
import uk.gov.justice.laa.datauserapi.contracts.response.CreateUserAccountResponse;
import uk.gov.justice.laa.datauserapi.exception.UserAlreadyExistsException;

import java.util.UUID;

@Service
public class UserAccountPersistenceService {
    private final UserCreationRepository repository;
    private final UserAccountCreationMapper mapper;

    public UserAccountPersistenceService(UserCreationRepository repository, UserAccountCreationMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional
    public CreateUserAccountResponse create(CreateUserAccountCommand command, UUID identityId, String actorOid) {
        repository.lockEmail(command.email());
        repository.findExisting(command.email()).map(mapper::toExistingResponse)
                .ifPresent(existing -> {
                    throw new UserAlreadyExistsException(existing);
                });
        UserAccountCreationData created = repository.insert(command, identityId, actorOid);
        return mapper.toCreatedResponse(created);
    }
}

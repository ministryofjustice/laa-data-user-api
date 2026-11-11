package uk.gov.justice.laa.datauserapi.application.command.service;

import lombok.extern.slf4j.Slf4j;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.command.mapper.ConvertToMultiFirmDomainMapper;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.EntraUserCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.convertmultifirm.ConvertToMultiFirmCommand;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.exception.ConflictException;
import uk.gov.justice.laa.datauserapi.exception.ResourceNotFoundException;

@Slf4j
@Service
public class ConvertToMultiFirmService {

    private final EntraUserCommandRepository entraUserCommandRepository;
    private final ConvertToMultiFirmDomainMapper mapper;
    private final EntityManager entityManager;

    public ConvertToMultiFirmService(EntraUserCommandRepository entraUserCommandRepository,
                                     ConvertToMultiFirmDomainMapper mapper, EntityManager entityManager) {
        this.entraUserCommandRepository = entraUserCommandRepository;
        this.mapper = mapper;
        this.entityManager = entityManager;
    }

    @Transactional
    public CommandResult convert(ConvertToMultiFirmCommand command, AuthenticatedEntraUser actor) {
        log.info("Converting user {} to multi-firm", command.userEntraObjectId());

        // Refresh a target cached by pre-handler policies after acquiring the row lock.
        EntraUser user = entraUserCommandRepository.findByEntraOidForUpdate(mapper.toEntraOid(command))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User account not found for oid: " + command.userEntraObjectId()));
        entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);

        if (user.isMultiFirmUser()) {
            log.warn("User {} is already multi-firm", command.userEntraObjectId());
            throw new ConflictException("User is already a multi-firm user");
        }

        user.convertToMultiFirm(actor.entraOid());
        entraUserCommandRepository.save(user);

        return mapper.toCommandResult(user);
    }

}

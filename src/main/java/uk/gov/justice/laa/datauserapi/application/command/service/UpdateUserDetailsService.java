package uk.gov.justice.laa.datauserapi.application.command.service;

import static net.logstash.logback.argument.StructuredArguments.kv;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.command.mapper.UpdateUserDetailsMapper;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.EntraUserCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.UpdateUserDetailsCommand;
import uk.gov.justice.laa.datauserapi.client.ts.TechServicesClient;
import uk.gov.justice.laa.datauserapi.client.ts.response.ChangeAccountEnabledResponse;
import uk.gov.justice.laa.datauserapi.client.ts.response.TechServicesApiResponse;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.exception.ResourceNotFoundException;
import uk.gov.justice.laa.datauserapi.exception.TechServicesClientException;

import java.time.LocalDateTime;

@Slf4j
@Service
public class UpdateUserDetailsService {

    private final EntraUserCommandRepository entraUserCommandRepository;
    private final UpdateUserDetailsMapper updateUserDetailsMapper;
    private final TechServicesClient techServicesClient;

    public UpdateUserDetailsService(EntraUserCommandRepository entraUserCommandRepository,
                                    UpdateUserDetailsMapper updateUserDetailsMapper,
                                    TechServicesClient techServicesClient) {
        this.entraUserCommandRepository = entraUserCommandRepository;
        this.updateUserDetailsMapper = updateUserDetailsMapper;
        this.techServicesClient = techServicesClient;
    }

    @Transactional(rollbackFor = TechServicesClientException.class)
    public CommandResult update(UpdateUserDetailsCommand command, AuthenticatedEntraUser actor) {
        log.info("Handling update user details command",
                kv("entraOid", command.userEntraObjectId()),
                kv("actorEntraOid", actor.entraOid()));
        String actorId = String.valueOf(actor.entraOid());

        EntraUser user = entraUserCommandRepository.findByEntraOid(String.valueOf(command.userEntraObjectId()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User account not found for oid: " + command.userEntraObjectId()));

        if (command.email() != null && !command.email().equalsIgnoreCase(user.getEmail())
                && entraUserCommandRepository.existsByEmailIgnoreCaseAndIdNot(command.email(), user.getId())) {
            log.warn("Email already in use; cannot update user",
                    kv("entraOid", command.userEntraObjectId()));
            return CommandResult.failure("Email address is already in use by another user");
        }

        TechServicesApiResponse<ChangeAccountEnabledResponse> response = techServicesClient
                .updateUserDetails(String.valueOf(command.userEntraObjectId()), command.firstName(), command.lastName(), command.email());

        if (response.isSuccess()) {
            if (command.firstName() != null) {
                user.setFirstName(command.firstName());
            }
            if (command.lastName() != null) {
                user.setLastName(command.lastName());
            }
            if (command.email() != null) {
                user.setEmail(command.email());
            }
            user.setLastModified(LocalDateTime.now());
            user.setLastModifiedBy(actorId);
            entraUserCommandRepository.save(user);

            log.info("Updated user details in database",
                    kv("entraOid", command.userEntraObjectId()),
                    kv("actorEntraOid", actorId));
            return updateUserDetailsMapper.toCommandResult(user);
        } else {
            throw new RuntimeException("Tech Services API call failed: " + response.getError());
        }

    }
}

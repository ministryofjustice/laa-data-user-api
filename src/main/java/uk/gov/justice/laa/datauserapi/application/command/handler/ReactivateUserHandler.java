package uk.gov.justice.laa.datauserapi.application.command.handler;

import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.command.mapper.ReactivateUserMapper;
import uk.gov.justice.laa.datauserapi.application.command.service.UserCommandService;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.EntraUserCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.UserAccountStatusAuditRepository;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.ReactivateUserCommand;
import uk.gov.justice.laa.datauserapi.client.ts.TechServicesClient;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.entity.UserAccountStatusAudit;
import uk.gov.justice.laa.datauserapi.exception.ResourceNotFoundException;
import uk.gov.justice.laa.datauserapi.exception.TechServicesClientException;

import java.util.Objects;
import java.util.UUID;

/**
 * Provider Admin firm scoping (no multi-firm targets, same firm only) is enforced by
 * {@code ProviderAdminFirmScopePolicy} via {@code @PolicyGuarded} on {@link ReactivateUserCommand} before this handler runs.
 */
@Slf4j
@Component
public class ReactivateUserHandler implements CommandHandler<ReactivateUserCommand> {

    private final EntraUserCommandRepository entraUserCommandRepository;
    private final UserAccountStatusAuditRepository auditRepository;
    private final TechServicesClient techServicesClient;
    private final ReactivateUserMapper mapper;
    private final ModelMapper modelMapper;
    private final UserCommandService userCommandService;

    public ReactivateUserHandler(
            EntraUserCommandRepository entraUserCommandRepository,
            UserAccountStatusAuditRepository auditRepository, TechServicesClient techServicesClient,
            ReactivateUserMapper mapper, ModelMapper modelMapper, UserCommandService userCommandService) {
        this.entraUserCommandRepository = entraUserCommandRepository;
        this.auditRepository = auditRepository;
        this.techServicesClient = techServicesClient;
        this.mapper = mapper;
        this.modelMapper = modelMapper;
        this.userCommandService = userCommandService;
    }

    @Override
    @Transactional(rollbackFor = TechServicesClientException.class)
    public CommandResult handle(ReactivateUserCommand command, AuthenticatedEntraUser actor) {
        log.info("Enabling user account with ID: {}", command.userEntraObjectId());

        if (Objects.equals(UUID.fromString(actor.entraOid()), command.userEntraObjectId())) {
            log.warn("User can not reactive self: {}", actor.entraOid());
            return CommandResult.failure("User can not reactive self");
        }

        boolean isInternalUser = userCommandService.isInternalUser(command.userEntraObjectId());
        if (isInternalUser) {
            log.warn("Internal user can not be activated: {}, by: {}", command.userEntraObjectId(), actor.entraOid());
            return CommandResult.failure("User can not reactive internal user");
        }

        EntraUser user = entraUserCommandRepository.findByEntraOid(String.valueOf(command.userEntraObjectId()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User account not found for oid: " + command.userEntraObjectId()));
        EntraUserDto userDto = modelMapper.map(user, EntraUserDto.class);
        techServicesClient.reactivateUser(userDto);

        user.reactivate(actor.entraOid());
        entraUserCommandRepository.save(user);

        UserAccountStatusAudit audit = mapper.toAuditEntity(user, actor.entraOid(), command.comments());
        auditRepository.save(audit);

        log.info("User account not found for oid: {}", command.userEntraObjectId());

        return mapper.toCommandResult(user);
    }
}

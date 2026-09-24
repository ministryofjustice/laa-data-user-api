package uk.gov.justice.laa.datauserapi.application.command.handler;

import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.command.mapper.DisableUserMapper;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.EntraUserCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.UserAccountStatusAuditRepository;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.DisableUserCommand;
import uk.gov.justice.laa.datauserapi.client.ts.TechServicesClient;
import uk.gov.justice.laa.datauserapi.contracts.domain.DisableUserReason;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.entity.UserAccountStatusAudit;
import uk.gov.justice.laa.datauserapi.exception.ResourceNotFoundException;
import uk.gov.justice.laa.datauserapi.exception.TechServicesClientException;
import uk.gov.justice.laa.datauserapi.model.DisableType;
import uk.gov.justice.laa.datauserapi.service.DisableTypeResolver;

import java.util.UUID;

@Slf4j
@Component
public class DisableUserHandler implements CommandHandler<DisableUserCommand> {

    private final EntraUserCommandRepository entraUserCommandRepository;
    private final UserAccountStatusAuditRepository auditRepository;
    private final DisableUserMapper disableUserMapper;
    private final DisableTypeResolver disableTypeResolver;
    private final ModelMapper modelMapper;
    private final TechServicesClient techServicesClient;

    public DisableUserHandler(
            EntraUserCommandRepository entraUserCommandRepository,
            UserAccountStatusAuditRepository auditRepository,
            DisableUserMapper disableUserMapper,
            DisableTypeResolver disableTypeResolver,
            ModelMapper modelMapper,
            TechServicesClient techServicesClient) {
        this.entraUserCommandRepository = entraUserCommandRepository;
        this.auditRepository = auditRepository;
        this.disableUserMapper = disableUserMapper;
        this.disableTypeResolver = disableTypeResolver;
        this.modelMapper = modelMapper;
        this.techServicesClient = techServicesClient;
    }

    @Override
    @Transactional(rollbackFor = TechServicesClientException.class)
    public CommandResult handle(DisableUserCommand command, String actorIdStr) {
        log.info("Handling disable user command for user: {}", command.userEntraObjectId());
        EntraUser user = entraUserCommandRepository.findById(command.userEntraObjectId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User account not found with ID: " + command.userEntraObjectId()));
        DisableUserReason disableReason = command.disableReason();

        UUID actorId = UUID.fromString(actorIdStr);
        EntraUser actor = entraUserCommandRepository.findById(actorId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Actor user account not found with ID: " + actorId));

        EntraUserDto userDto = modelMapper.map(user, EntraUserDto.class);
        techServicesClient.disableUser(userDto, command.disableReason().name());

        DisableType disableType = disableTypeResolver.resolve(actor);
        user.disable(actorIdStr, disableReason, disableType);
        entraUserCommandRepository.save(user);

        UserAccountStatusAudit audit = disableUserMapper.toAuditEntity(user, command, actorIdStr);
        auditRepository.save(audit);
        log.info("User account disabled with ID: {} by: {} with reason: {}", user.getId(), actorIdStr, disableReason);

        return disableUserMapper.toCommandResult(user);
    }
}

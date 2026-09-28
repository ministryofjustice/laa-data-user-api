package uk.gov.justice.laa.datauserapi.application.command.handler;

import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.command.mapper.DeactivateUserMapper;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.ReactivateUserCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.UserAccountStatusAuditRepository;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.DeactivateUserCommand;
import uk.gov.justice.laa.datauserapi.client.ts.TechServicesClient;
import uk.gov.justice.laa.datauserapi.contracts.domain.DeactivateUserReason;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.entity.UserAccountStatusAudit;
import uk.gov.justice.laa.datauserapi.exception.ResourceNotFoundException;
import uk.gov.justice.laa.datauserapi.exception.TechServicesClientException;
import uk.gov.justice.laa.datauserapi.model.DeactivationType;
import uk.gov.justice.laa.datauserapi.service.DeactivationTypeResolver;

import java.util.UUID;

@Slf4j
@Component
public class DeactivateUserHandler implements CommandHandler<DeactivateUserCommand> {

    private final ReactivateUserCommandRepository reactivateUserCommandRepository;
    private final UserAccountStatusAuditRepository auditRepository;
    private final DeactivateUserMapper deactivateUserMapper;
    private final DeactivationTypeResolver deactivationTypeResolver;
    private final ModelMapper modelMapper;
    private final TechServicesClient techServicesClient;

    public DeactivateUserHandler(
            ReactivateUserCommandRepository reactivateUserCommandRepository,
            UserAccountStatusAuditRepository auditRepository,
            DeactivateUserMapper deactivateUserMapper,
            DeactivationTypeResolver deactivationTypeResolver,
            ModelMapper modelMapper,
            TechServicesClient techServicesClient) {
        this.reactivateUserCommandRepository = reactivateUserCommandRepository;
        this.auditRepository = auditRepository;
        this.deactivateUserMapper = deactivateUserMapper;
        this.deactivationTypeResolver = deactivationTypeResolver;
        this.modelMapper = modelMapper;
        this.techServicesClient = techServicesClient;
    }

    @Override
    @Transactional(rollbackFor = TechServicesClientException.class)
    public CommandResult handle(DeactivateUserCommand command, String actorIdStr) {
        log.info("Handling deactivate user command for user: {}", command.userEntraObjectId());
        EntraUser user = reactivateUserCommandRepository.findById(command.userEntraObjectId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User account not found with ID: " + command.userEntraObjectId()));
        DeactivateUserReason deactivateUserReason = command.deactivateReason();

        UUID actorId = UUID.fromString(actorIdStr);
        EntraUser actor = reactivateUserCommandRepository.findById(actorId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Actor user account not found with ID: " + actorId));

        EntraUserDto userDto = modelMapper.map(user, EntraUserDto.class);
        techServicesClient.deactivateUser(userDto, command.deactivateReason().name());

        DeactivationType deactivationType = deactivationTypeResolver.resolve(actor);
        user.deactivate(actorIdStr, deactivationType);
        reactivateUserCommandRepository.save(user);

        UserAccountStatusAudit audit = deactivateUserMapper.toAuditEntity(user, command, actorIdStr);
        auditRepository.save(audit);
        log.info("User account deactivated with ID: {} by: {} with reason: {}", user.getId(), actorIdStr, deactivateUserReason);

        return deactivateUserMapper.toCommandResult(user);
    }
}

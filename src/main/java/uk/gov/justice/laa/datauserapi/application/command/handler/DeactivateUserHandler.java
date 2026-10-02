package uk.gov.justice.laa.datauserapi.application.command.handler;

import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.command.mapper.DeactivateUserMapper;
import uk.gov.justice.laa.datauserapi.application.command.service.UserCommandService;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.EntraUserCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.UserAccountStatusAuditRepository;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.DeactivateUserCommand;
import uk.gov.justice.laa.datauserapi.client.ts.TechServicesClient;
import uk.gov.justice.laa.datauserapi.contracts.domain.DeactivateUserReason;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.entity.UserAccountStatusAudit;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.exception.ResourceNotFoundException;
import uk.gov.justice.laa.datauserapi.exception.TechServicesClientException;
import uk.gov.justice.laa.datauserapi.model.DeactivationType;
import uk.gov.justice.laa.datauserapi.service.DeactivationTypeResolver;

import java.util.Objects;
import java.util.UUID;

@Slf4j
@Component
public class DeactivateUserHandler implements CommandHandler<DeactivateUserCommand> {

    private final EntraUserCommandRepository entraUserCommandRepository;
    private final UserAccountStatusAuditRepository auditRepository;
    private final DeactivateUserMapper deactivateUserMapper;
    private final DeactivationTypeResolver deactivationTypeResolver;
    private final ModelMapper modelMapper;
    private final TechServicesClient techServicesClient;
    private final UserCommandService userCommandService;

    public DeactivateUserHandler(
            EntraUserCommandRepository entraUserCommandRepository,
            UserAccountStatusAuditRepository auditRepository,
            DeactivateUserMapper deactivateUserMapper,
            DeactivationTypeResolver deactivationTypeResolver,
            ModelMapper modelMapper,
            TechServicesClient techServicesClient,
            UserCommandService userCommandService) {
        this.entraUserCommandRepository = entraUserCommandRepository;
        this.auditRepository = auditRepository;
        this.deactivateUserMapper = deactivateUserMapper;
        this.deactivationTypeResolver = deactivationTypeResolver;
        this.modelMapper = modelMapper;
        this.techServicesClient = techServicesClient;
        this.userCommandService = userCommandService;
    }

    @Override
    @Transactional(rollbackFor = TechServicesClientException.class)
    public CommandResult handle(DeactivateUserCommand command, String actorIdStr) {
        log.info("Handling deactivate user command for user: {}", command.userEntraObjectId());

        if (Objects.equals(UUID.fromString(actorIdStr), command.userEntraObjectId())) {
            log.warn("User can not reactive self: {}", actorIdStr);
            return CommandResult.failure("User can not reactive self");
        }

        boolean isInternalUser = userCommandService.isInternalUser(command.userEntraObjectId());
        if (isInternalUser) {
            log.warn("Internal user can not be deactivated: {}, by: {}", command.userEntraObjectId(), actorIdStr);
            return CommandResult.failure("User can not deactivate internal user");
        }

        boolean isActorExternalUser = userCommandService.isExternalUser(UUID.fromString(actorIdStr));
        if (isActorExternalUser) {
            UserProfile actorProfile = userCommandService.getActiveUserProfile(UUID.fromString(actorIdStr));
            UserProfile targetUserProfile = userCommandService.getActiveUserProfile(command.userEntraObjectId());

            if (targetUserProfile.getEntraUser().isMultiFirmUser()) {
                log.warn("User {} can not deactivate multi-firm user: {}", command.userEntraObjectId(), actorIdStr);
                return CommandResult.failure("User can not deactivate multi-firm user");
            }

            UUID actorFirmId = actorProfile.getFirm().getId();
            UUID targetUserFirmId = targetUserProfile.getFirm().getId();
            if (!targetUserFirmId.equals(actorFirmId)) {
                log.warn("User {} can not deactivate user {} from different firm", actorIdStr, command.userEntraObjectId());
                return CommandResult.failure("User can not deactivate user from different firm");
            }
        }

        EntraUser user = entraUserCommandRepository.findByEntraOid(String.valueOf(command.userEntraObjectId()))
                .orElseThrow(() -> new ResourceNotFoundException("User account not found for oid: " + command.userEntraObjectId()));

        EntraUserDto userDto = modelMapper.map(user, EntraUserDto.class);
        techServicesClient.deactivateUser(userDto, command.deactivateReason().name());

        UUID actorId = UUID.fromString(actorIdStr);
        EntraUser actor = entraUserCommandRepository.findByEntraOid(String.valueOf(actorId))
                .orElseThrow(() -> new ResourceNotFoundException("Acting user account not found for oid: " + actorId));
        DeactivationType deactivationType = deactivationTypeResolver.resolve(actor);
        user.deactivate(actorIdStr, deactivationType);
        entraUserCommandRepository.save(user);

        DeactivateUserReason deactivateUserReason = command.deactivateReason();
        UserAccountStatusAudit audit = deactivateUserMapper.toAuditEntity(user, command, actorIdStr);
        auditRepository.save(audit);
        log.info("User account deactivated with ID: {} by: {} with reason: {}", user.getId(), actorIdStr, deactivateUserReason);

        return deactivateUserMapper.toCommandResult(user);
    }
}

package uk.gov.justice.laa.datauserapi.application.command.handler;

import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.command.mapper.ReactivateUserMapper;
import uk.gov.justice.laa.datauserapi.application.command.service.UserCommandService;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.ReactivateUserCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.UserAccountStatusAuditRepository;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.ReactivateUserCommand;
import uk.gov.justice.laa.datauserapi.client.ts.TechServicesClient;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.entity.UserAccountStatusAudit;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.exception.ResourceNotFoundException;
import uk.gov.justice.laa.datauserapi.exception.TechServicesClientException;

import java.util.Objects;
import java.util.UUID;

@Slf4j
@Component
public class ReactivateUserHandler implements CommandHandler<ReactivateUserCommand> {

    private final ReactivateUserCommandRepository reactivateUserCommandRepository;
    private final UserAccountStatusAuditRepository auditRepository;
    private final TechServicesClient techServicesClient;
    private final ReactivateUserMapper mapper;
    private final ModelMapper modelMapper;
    private final UserCommandService userCommandService;

    public ReactivateUserHandler(
            ReactivateUserCommandRepository reactivateUserCommandRepository,
            UserAccountStatusAuditRepository auditRepository, TechServicesClient techServicesClient,
            ReactivateUserMapper mapper, ModelMapper modelMapper, UserCommandService userCommandService) {
        this.reactivateUserCommandRepository = reactivateUserCommandRepository;
        this.auditRepository = auditRepository;
        this.techServicesClient = techServicesClient;
        this.mapper = mapper;
        this.modelMapper = modelMapper;
        this.userCommandService = userCommandService;
    }

    @Override
    @Transactional(rollbackFor = TechServicesClientException.class)
    public CommandResult handle(ReactivateUserCommand command, String actorIdStr) {
        log.info("Enabling user account with ID: {}", command.userEntraObjectId());

        if (Objects.equals(UUID.fromString(actorIdStr), command.userEntraObjectId())) {
            log.warn("User can not reactive self: {}", actorIdStr);
            return CommandResult.failure("User can not reactive self");
        }

        boolean isInternalUser = userCommandService.isInternalUser(command.userEntraObjectId());
        if (isInternalUser) {
            log.warn("Internal user can not be activated: {}, by: {}", command.userEntraObjectId(), actorIdStr);
            return CommandResult.failure("User can not reactive internal user");
        }

        boolean isActorExternalUser = userCommandService.isExternalUser(UUID.fromString(actorIdStr));
        if (isActorExternalUser) {
            UserProfile actorProfile = userCommandService.getActiveUserProfile(UUID.fromString(actorIdStr));
            UserProfile targetUserProfile = userCommandService.getActiveUserProfile(command.userEntraObjectId());

            if (targetUserProfile.getEntraUser().isMultiFirmUser()) {
                log.warn("User {} can not reactive multi-firm user: {}", command.userEntraObjectId(), actorIdStr);
                return CommandResult.failure("User can not reactive multi-firm user");
            }

            UUID actorFirmId = actorProfile.getFirm().getId();
            UUID targetUserFirmId = targetUserProfile.getFirm().getId();
            if (!targetUserFirmId.equals(actorFirmId)) {
                log.warn("User {} can not reactivate user {} from different firm", actorIdStr, command.userEntraObjectId());
                return CommandResult.failure("User can not reactive user from different firm");
            }
        }

        EntraUser user = reactivateUserCommandRepository.findById(command.userEntraObjectId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User account not found with ID: " + command.userEntraObjectId()));
        EntraUserDto userDto = modelMapper.map(user, EntraUserDto.class);
        techServicesClient.reactivateUser(userDto);

        user.reactivate(actorIdStr);
        reactivateUserCommandRepository.save(user);

        UserAccountStatusAudit audit = mapper.toAuditEntity(user, actorIdStr, command.comments());
        auditRepository.save(audit);

        log.info("User account enabled with ID: {}", command.userEntraObjectId());

        return mapper.toCommandResult(user);
    }
}

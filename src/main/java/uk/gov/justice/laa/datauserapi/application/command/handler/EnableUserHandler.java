package uk.gov.justice.laa.datauserapi.application.command.handler;

import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.command.mapper.EnableUserMapper;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.EntraUserCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.UserAccountStatusAuditRepository;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.EnableUserCommand;
import uk.gov.justice.laa.datauserapi.client.ts.TechServicesClient;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.entity.UserAccountStatusAudit;
import uk.gov.justice.laa.datauserapi.exception.ResourceNotFoundException;
import uk.gov.justice.laa.datauserapi.exception.TechServicesClientException;

@Slf4j
@Component
public class EnableUserHandler implements CommandHandler<EnableUserCommand> {

    private final EntraUserCommandRepository entraUserCommandRepository;
    private final UserAccountStatusAuditRepository auditRepository;
    private final TechServicesClient techServicesClient;
    private final EnableUserMapper mapper;
    private final ModelMapper modelMapper;

    public EnableUserHandler(
            EntraUserCommandRepository entraUserCommandRepository,
            UserAccountStatusAuditRepository auditRepository, TechServicesClient techServicesClient,
            EnableUserMapper mapper, ModelMapper modelMapper) {
        this.entraUserCommandRepository = entraUserCommandRepository;
        this.auditRepository = auditRepository;
        this.techServicesClient = techServicesClient;
        this.mapper = mapper;
        this.modelMapper = modelMapper;
    }

    @Override
    @Transactional(rollbackFor = TechServicesClientException.class)
    public CommandResult handle(EnableUserCommand command, String actorIdStr) {
        EntraUser user = entraUserCommandRepository.findById(command.userEntraObjectId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User account not found with ID: " + command.userEntraObjectId()));

        log.info("Enabling user account with ID: {}", command.userEntraObjectId());

        EntraUserDto userDto = modelMapper.map(user, EntraUserDto.class);
        techServicesClient.enableUser(userDto);

        user.enable(actorIdStr);
        entraUserCommandRepository.save(user);

        UserAccountStatusAudit audit = mapper.toAuditEntity(user, actorIdStr, command.comments());
        auditRepository.save(audit);

        log.info("User account enabled with ID: {}", command.userEntraObjectId());

        return mapper.toCommandResult(user);
    }
}

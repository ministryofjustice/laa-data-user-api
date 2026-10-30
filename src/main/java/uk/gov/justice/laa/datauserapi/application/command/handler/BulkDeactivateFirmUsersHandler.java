package uk.gov.justice.laa.datauserapi.application.command.handler;

import static net.logstash.logback.argument.StructuredArguments.kv;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.command.mapper.DeactivateUserMapper;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.EntraUserCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.FirmCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.UserAccountStatusAuditRepository;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.BulkDeactivateFirmUsersCommand;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.DeactivateUserCommand;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.entity.UserAccountStatusAudit;
import uk.gov.justice.laa.datauserapi.exception.ResourceNotFoundException;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class BulkDeactivateFirmUsersHandler implements CommandHandler<BulkDeactivateFirmUsersCommand> {

    private final FirmCommandRepository firmCommandRepository;
    private final EntraUserCommandRepository entraUserCommandRepository;
    private final UserAccountStatusAuditRepository auditRepository;
    private final DeactivateUserMapper deactivateUserMapper;
    private final DeactivateUserHandler deactivateUserHandler;

    public BulkDeactivateFirmUsersHandler(
            FirmCommandRepository firmCommandRepository,
            EntraUserCommandRepository entraUserCommandRepository,
            UserAccountStatusAuditRepository auditRepository,
            DeactivateUserMapper deactivateUserMapper,
            DeactivateUserHandler deactivateUserHandler) {
        this.firmCommandRepository = firmCommandRepository;
        this.entraUserCommandRepository = entraUserCommandRepository;
        this.auditRepository = auditRepository;
        this.deactivateUserMapper = deactivateUserMapper;
        this.deactivateUserHandler = deactivateUserHandler;
    }

    // Each user is deactivated in its own transaction so one failure does not roll back the rest.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Override
    public CommandResult handle(BulkDeactivateFirmUsersCommand command, AuthenticatedEntraUser actor) {
        String firmCode = command.firmId();
        if (!firmCommandRepository.existsByCode(firmCode)) {
            throw new ResourceNotFoundException("Firm not found with ID: " + firmCode);
        }

        List<EntraUser> users = entraUserCommandRepository.findUsersByFirmCode(firmCode);
        int deactivated = 0;
        int alreadyDeactivated = 0;
        int failed = 0;

        for (EntraUser user : users) {
            UUID userEntraObjectId = UUID.fromString(user.getEntraOid());
            if (!user.isActive()) {
                UserAccountStatusAudit audit = deactivateUserMapper.toAuditEntity(
                        user, command.deactivateReason(), actor.entraOid());
                auditRepository.save(audit);
                alreadyDeactivated++;
                continue;
            }

            DeactivateUserCommand deactivateCommand = DeactivateUserCommand.builder()
                    .userEntraObjectId(userEntraObjectId)
                    .deactivateReason(command.deactivateReason())
                    .build();
            CommandResult result;
            try {
                result = deactivateUserHandler.handle(deactivateCommand, actor);
            } catch (AccessDeniedException ex) {
                result = CommandResult.failure("Not permitted to deactivate user");
            }
            if (result.success()) {
                deactivated++;
            } else {
                failed++;
                log.warn("Could not bulk-deactivate user",
                        kv("entraOid", userEntraObjectId),
                        kv("firmCode", firmCode));
            }
        }

        if (failed > 0) {
            return CommandResult.failure(String.format(
                    "Deactivated %d users; audited %d already-deactivated users; failed to deactivate %d users "
                            + "for firm '%s'",
                    deactivated, alreadyDeactivated, failed, firmCode));
        }
        return CommandResult.success(String.format(
                "Deactivated %d users; audited %d already-deactivated users for firm '%s'",
                deactivated, alreadyDeactivated, firmCode));
    }
}

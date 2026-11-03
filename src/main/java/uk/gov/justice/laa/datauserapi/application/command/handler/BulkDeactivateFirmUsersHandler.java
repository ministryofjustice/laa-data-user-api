package uk.gov.justice.laa.datauserapi.application.command.handler;

import static net.logstash.logback.argument.StructuredArguments.kv;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.datauserapi.application.command.mapper.DeactivateUserMapper;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.EntraUserCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.FirmCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.UserAccountStatusAuditRepository;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.BulkDeactivateFirmUsersCommand;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.DeactivateUserCommand;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountStatus;
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
            switch (processUser(user, command, actor, firmCode)) {
                case DEACTIVATED -> deactivated++;
                case ALREADY_DEACTIVATED -> alreadyDeactivated++;
                default -> failed++;
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

    private UserOutcome processUser(EntraUser user, BulkDeactivateFirmUsersCommand command,
                                    AuthenticatedEntraUser actor, String firmCode) {
        // Isolate failures per user so one bad user cannot abort the rest of the batch
        try {
            UUID userEntraObjectId = UUID.fromString(user.getEntraOid());
            if (user.getUserAccountStatus() == UserAccountStatus.DEACTIVATED) {
                UserAccountStatusAudit audit = deactivateUserMapper.toAuditEntity(
                        user, command.deactivateReason(), actor.entraOid());
                auditRepository.save(audit);
                return UserOutcome.ALREADY_DEACTIVATED;
            }

            DeactivateUserCommand deactivateCommand = DeactivateUserCommand.builder()
                    .userEntraObjectId(userEntraObjectId)
                    .deactivateReason(command.deactivateReason())
                    .build();
            CommandResult result = deactivateUserHandler.handle(deactivateCommand, actor);
            if (result.success()) {
                return UserOutcome.DEACTIVATED;
            }
            log.warn("Could not bulk-deactivate user",
                    kv("entraOid", user.getEntraOid()),
                    kv("firmCode", firmCode),
                    kv("reason", result.message()));
        } catch (Exception ex) {
            log.error("Exception occurred while deactivating user",
                    kv("entraOid", user.getEntraOid()),
                    kv("firmCode", firmCode),
                    ex);
        }
        return UserOutcome.FAILED;
    }

    private enum UserOutcome {
        DEACTIVATED,
        ALREADY_DEACTIVATED,
        FAILED
    }
}

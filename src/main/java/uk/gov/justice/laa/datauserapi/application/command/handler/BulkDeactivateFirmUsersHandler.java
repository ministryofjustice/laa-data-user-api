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
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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
        Map<Outcome, Long> counts = users.stream()
                .collect(Collectors.groupingBy(user -> processUser(user, command, actor), Collectors.counting()));
        long deactivated = counts.getOrDefault(Outcome.DEACTIVATED, 0L);
        long alreadyDeactivated = counts.getOrDefault(Outcome.ALREADY_DEACTIVATED, 0L);
        long failed = counts.getOrDefault(Outcome.FAILED, 0L);

        String summary = String.format("Deactivated %d users; audited %d already-deactivated users",
                deactivated, alreadyDeactivated);
        if (failed > 0) {
            return CommandResult.failure(String.format(
                    "%s; failed to deactivate %d users for firm '%s'", summary, failed, firmCode));
        }
        return CommandResult.success(String.format("%s for firm '%s'", summary, firmCode));
    }

    private Outcome processUser(EntraUser user, BulkDeactivateFirmUsersCommand command,
                                AuthenticatedEntraUser actor) {
        try {
            if (user.getUserAccountStatus() == UserAccountStatus.DEACTIVATED) {
                UserAccountStatusAudit audit = deactivateUserMapper.toAuditEntity(
                        user, command.deactivateReason(), actor.entraOid());
                auditRepository.save(audit);
                return Outcome.ALREADY_DEACTIVATED;
            }

            DeactivateUserCommand deactivateCommand = DeactivateUserCommand.builder()
                    .userEntraObjectId(UUID.fromString(user.getEntraOid()))
                    .deactivateReason(command.deactivateReason())
                    .build();
            CommandResult result = deactivateUserHandler.handle(deactivateCommand, actor);
            if (result.success()) {
                return Outcome.DEACTIVATED;
            }
            log.warn("Could not bulk-deactivate user",
                    kv("entraOid", user.getEntraOid()),
                    kv("firmCode", command.firmId()));
        } catch (RuntimeException e) {
            log.error("Error while bulk-deactivating user",
                    kv("entraOid", user.getEntraOid()),
                    kv("firmCode", command.firmId()),
                    e);
        }
        return Outcome.FAILED;
    }

    private enum Outcome {
        DEACTIVATED,
        ALREADY_DEACTIVATED,
        FAILED
    }
}

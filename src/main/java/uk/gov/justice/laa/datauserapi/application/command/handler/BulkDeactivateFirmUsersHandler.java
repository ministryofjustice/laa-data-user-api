package uk.gov.justice.laa.datauserapi.application.command.handler;

import static net.logstash.logback.argument.StructuredArguments.kv;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.EntraUserCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.FirmCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.BulkDeactivateFirmUsersCommand;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.DeactivateUserCommand;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.exception.ResourceNotFoundException;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class BulkDeactivateFirmUsersHandler implements CommandHandler<BulkDeactivateFirmUsersCommand> {

    private final FirmCommandRepository firmCommandRepository;
    private final EntraUserCommandRepository entraUserCommandRepository;
    private final DeactivateUserHandler deactivateUserHandler;

    public BulkDeactivateFirmUsersHandler(
            FirmCommandRepository firmCommandRepository,
            EntraUserCommandRepository entraUserCommandRepository,
            DeactivateUserHandler deactivateUserHandler) {
        this.firmCommandRepository = firmCommandRepository;
        this.entraUserCommandRepository = entraUserCommandRepository;
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
        int failed = 0;

        for (EntraUser user : users) {
            try {
                UUID userEntraObjectId = UUID.fromString(user.getEntraOid());
                DeactivateUserCommand deactivateCommand = DeactivateUserCommand.builder()
                        .userEntraObjectId(userEntraObjectId)
                        .deactivateReason(command.deactivateReason())
                        .build();
                CommandResult result = deactivateUserHandler.handle(deactivateCommand, actor);
                if (result.success()) {
                    deactivated++;
                } else {
                    failed++;
                    log.warn("Could not bulk-deactivate user",
                            kv("entraOid", userEntraObjectId),
                            kv("firmCode", firmCode));
                }
            } catch (Exception ex) {
                failed++;
                log.warn("Exception occurred while deactivating user",
                        kv("entraOid", user.getEntraOid()),
                        kv("firmCode", firmCode),
                        ex);
            }

        }

        if (failed > 0) {
            return CommandResult.failure(String.format(
                    "Deactivated %d users; failed to deactivate %d users for firm '%s'",
                    deactivated, failed, firmCode));
        }
        return CommandResult.success(String.format(
                "Deactivated %d users for firm '%s'", deactivated, firmCode));
    }
}

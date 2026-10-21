package uk.gov.justice.laa.datauserapi.application.command.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.EntraUserCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.FirmCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.BulkDeactivateFirmUsersCommand;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.DeactivateUserCommand;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.exception.ResourceNotFoundException;
import uk.gov.justice.laa.datauserapi.model.SilasAccountStatus;

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

    @Override
    public CommandResult handle(BulkDeactivateFirmUsersCommand command, String actorOid) {
        String firmCode = command.firmId();
        if (!firmCommandRepository.existsByCode(firmCode)) {
            throw new ResourceNotFoundException("Firm not found with ID: " + firmCode);
        }

        List<UUID> userIds = entraUserCommandRepository.findActiveSingleFirmUserIdsByFirmCode(
                firmCode, SilasAccountStatus.ACTIVE);
        int deactivated = 0;
        int failed = 0;

        for (UUID userId : userIds) {
            DeactivateUserCommand deactivateCommand = DeactivateUserCommand.builder()
                    .userEntraObjectId(userId)
                    .deactivateReason(command.deactivateReason())
                    .build();
            CommandResult result = deactivateUserHandler.handle(deactivateCommand, actorOid);
            if (result.success()) {
                deactivated++;
            } else {
                failed++;
                log.warn("Could not bulk-deactivate user {} for firm {}", userId, firmCode);
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

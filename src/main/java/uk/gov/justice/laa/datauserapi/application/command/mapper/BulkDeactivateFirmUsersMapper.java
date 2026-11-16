package uk.gov.justice.laa.datauserapi.application.command.mapper;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.datauserapi.application.command.dto.BulkDeactivationResult;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.BulkDeactivateFirmUsersCommand;
import uk.gov.justice.laa.datauserapi.contracts.request.BulkDeactivateFirmUsersRequest;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;

@Component
public class BulkDeactivateFirmUsersMapper {

    public BulkDeactivateFirmUsersCommand toCommand(BulkDeactivateFirmUsersRequest request) {
        return new BulkDeactivateFirmUsersCommand(request.firmId(), request.deactivateReason());
    }

    public CommandResult toAcceptedResult() {
        return CommandResult.success("Bulk deactivation started");
    }

    public CommandResult toCommandResult(String firmCode, BulkDeactivationResult result) {
        if (result.failed() > 0) {
            return CommandResult.failure(String.format(
                    "Deactivated %d users; failed to deactivate %d users for firm '%s'",
                    result.deactivated(), result.failed(), firmCode));
        }
        return CommandResult.success(String.format("Deactivated %d users for firm '%s'", result.deactivated(), firmCode));
    }
}

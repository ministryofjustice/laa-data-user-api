package uk.gov.justice.laa.datauserapi.application.command.mapper;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.BulkDeactivateFirmUsersCommand;
import uk.gov.justice.laa.datauserapi.contracts.request.BulkDeactivateFirmUsersRequest;

@Component
public class BulkDeactivateFirmUsersMapper {

    public BulkDeactivateFirmUsersCommand toCommand(BulkDeactivateFirmUsersRequest request) {
        return new BulkDeactivateFirmUsersCommand(request.firmId(), request.deactivateReason());
    }
}

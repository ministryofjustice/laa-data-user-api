package uk.gov.justice.laa.datauserapi.application.command.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.command.mapper.BulkDeactivateFirmUsersMapper;
import uk.gov.justice.laa.datauserapi.application.command.service.BulkDeactivateFirmUsersService;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.BulkDeactivateFirmUsersCommand;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;

@Component
public class BulkDeactivateFirmUsersHandler implements CommandHandler<BulkDeactivateFirmUsersCommand, CommandResult> {

    private final BulkDeactivateFirmUsersService bulkService;
    private final BulkDeactivateFirmUsersMapper mapper;

    public BulkDeactivateFirmUsersHandler(BulkDeactivateFirmUsersService bulkService, BulkDeactivateFirmUsersMapper mapper) {
        this.bulkService = bulkService;
        this.mapper = mapper;
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CommandResult handle(BulkDeactivateFirmUsersCommand command, AuthenticatedEntraUser actor) {
        return mapper.toCommandResult(command.firmId(), bulkService.deactivate(command, actor));
    }
}

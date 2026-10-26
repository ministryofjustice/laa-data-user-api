package uk.gov.justice.laa.datauserapi.application.command.useraccount.convertmultifirm;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;

@Component
public class ConvertToMultiFirmDomainMapper {

    public String toEntraOid(ConvertToMultiFirmCommand command) {
        return String.valueOf(command.userEntraObjectId());
    }

    public CommandResult toCommandResult(EntraUser user) {
        return CommandResult.success(
                String.format("User account '%s' converted to multi-firm successfully", user.getEntraOid()));
    }
}

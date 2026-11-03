package uk.gov.justice.laa.datauserapi.application.command.mapper;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.UpdateUserDetailsCommand;
import uk.gov.justice.laa.datauserapi.contracts.request.UpdateUserDetailsRequest;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;

@Component
public class UpdateUserDetailsMapper {

    public UpdateUserDetailsCommand toUpdateUserDetailsCommand(UpdateUserDetailsRequest request) {
        return UpdateUserDetailsCommand.builder()
                .userEntraObjectId(request.userEntraObjectId())
                .firstName(trim(request.firstName()))
                .lastName(trim(request.lastName()))
                .email(trim(request.email()))
                .build();
    }

    public CommandResult toCommandResult(EntraUser user) {
        return CommandResult.success(String.format("User account '%s' details updated successfully", user.getEntraOid()));
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}

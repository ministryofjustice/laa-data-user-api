package uk.gov.justice.laa.datauserapi.application.command.mapper;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.datauserapi.application.command.dto.UserAccountCreationData;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.CreateUserAccountCommand;
import uk.gov.justice.laa.datauserapi.contracts.request.CreateUserAccountRequest;
import uk.gov.justice.laa.datauserapi.contracts.response.CreateUserAccountResponse;

import java.util.Locale;

@Component
public class UserAccountCreationMapper {

    public CreateUserAccountCommand toCommand(CreateUserAccountRequest request) {
        return new CreateUserAccountCommand(request.email().strip().toLowerCase(Locale.ROOT),
                request.firstName(), request.lastName(), request.userType(), request.multiFirmUser(), request.firmId());
    }

    public CreateUserAccountResponse toExistingResponse(UserAccountCreationData data) {
        return new CreateUserAccountResponse(false, "User already exists", data.entraOid(), data.profileId());
    }

    public CreateUserAccountResponse toCreatedResponse(UserAccountCreationData data) {
        return new CreateUserAccountResponse(true, "User created", data.entraOid(), data.profileId());
    }
}

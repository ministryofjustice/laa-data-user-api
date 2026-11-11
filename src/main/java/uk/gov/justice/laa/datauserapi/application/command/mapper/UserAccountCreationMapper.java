package uk.gov.justice.laa.datauserapi.application.command.mapper;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.datauserapi.application.command.dto.UserAccountCreationData;
import uk.gov.justice.laa.datauserapi.contracts.response.CreateUserAccountResponse;

@Component
public class UserAccountCreationMapper {

    public CreateUserAccountResponse toExistingResponse(UserAccountCreationData data) {
        return new CreateUserAccountResponse(false, "User already exists", data.entraOid(), data.profileId());
    }

    public CreateUserAccountResponse toCreatedResponse(UserAccountCreationData data) {
        return new CreateUserAccountResponse(true, "User created", data.entraOid(), data.profileId());
    }
}

package uk.gov.justice.laa.datauserapi.client.ts;

import java.util.UUID;
import uk.gov.justice.laa.datauserapi.contracts.request.CreateUserAccountCommand;

public interface EntraUserClient {
    Identity createUser(CreateUserAccountCommand command);

    record Identity(UUID id, boolean created) {}
}

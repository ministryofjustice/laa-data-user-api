package uk.gov.justice.laa.datauserapi.client.ts;

import java.util.UUID;

public interface EntraUserClient {
    Identity createUser(Registration registration);

    record Registration(String email, String firstName, String lastName) {}

    record Identity(UUID id, boolean created) {}
}

package uk.gov.justice.laa.datauserapi.client.ts;

import uk.gov.justice.laa.datauserapi.client.ts.response.ChangeAccountEnabledResponse;
import uk.gov.justice.laa.datauserapi.client.ts.response.ChangeAccountReactivationResponse;
import uk.gov.justice.laa.datauserapi.client.ts.response.TechServicesApiResponse;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import java.util.UUID;

public interface TechServicesClient {

    Identity createUser(Registration registration);

    record Registration(String email, String firstName, String lastName) {}

    record Identity(UUID id, boolean created) {}

    TechServicesApiResponse<ChangeAccountReactivationResponse> reactivateUser(EntraUserDto user);

    TechServicesApiResponse<ChangeAccountReactivationResponse> deactivateUser(EntraUserDto user, String reason);

    TechServicesApiResponse<ChangeAccountEnabledResponse> updateUserDetails(String entraOid, String firstName, String lastName, String email);

    void deleteRoleAssignment(EntraUserDto entraUser);
}

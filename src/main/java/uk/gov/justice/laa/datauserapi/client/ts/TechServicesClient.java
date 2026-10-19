package uk.gov.justice.laa.datauserapi.client.ts;

import uk.gov.justice.laa.datauserapi.client.ts.response.ChangeAccountEnabledResponse;
import uk.gov.justice.laa.datauserapi.client.ts.response.ChangeAccountReactivationResponse;
import uk.gov.justice.laa.datauserapi.client.ts.response.TechServicesApiResponse;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;

public interface TechServicesClient {

    TechServicesApiResponse<ChangeAccountReactivationResponse> reactivateUser(EntraUserDto user);

    TechServicesApiResponse<ChangeAccountReactivationResponse> deactivateUser(EntraUserDto user, String reason);

    TechServicesApiResponse<ChangeAccountEnabledResponse> updateUserDetails(String entraOid, String firstName, String lastName, String email);

}

package uk.gov.justice.laa.datauserapi.client.ts;

import uk.gov.justice.laa.datauserapi.client.ts.response.ChangeAccountEnabledResponse;
import uk.gov.justice.laa.datauserapi.client.ts.response.TechServicesApiResponse;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;

public interface TechServicesClient {

    TechServicesApiResponse<ChangeAccountEnabledResponse> enableUser(EntraUserDto user);

    TechServicesApiResponse<ChangeAccountEnabledResponse> disableUser(EntraUserDto user, String reason);

}

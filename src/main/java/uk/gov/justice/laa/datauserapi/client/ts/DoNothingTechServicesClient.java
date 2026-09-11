package uk.gov.justice.laa.datauserapi.client.ts;

import lombok.extern.slf4j.Slf4j;

import uk.gov.justice.laa.datauserapi.client.ts.response.ChangeAccountEnabledResponse;
import uk.gov.justice.laa.datauserapi.client.ts.response.TechServicesApiResponse;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;

@Slf4j
public class DoNothingTechServicesClient implements TechServicesClient {

    @Override
    public TechServicesApiResponse<ChangeAccountEnabledResponse> enableUser(EntraUserDto user) {
        return TechServicesApiResponse.success(ChangeAccountEnabledResponse.builder().success(true)
                .message("Successfully enabled user.")
                .build());
    }

    @Override
    public TechServicesApiResponse<ChangeAccountEnabledResponse> disableUser(EntraUserDto user, String reason) {
        return TechServicesApiResponse.success(ChangeAccountEnabledResponse.builder().success(true)
                .message("Successfully disabled user.")
                .build());
    }

}

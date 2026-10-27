package uk.gov.justice.laa.datauserapi.client.ts;

import lombok.extern.slf4j.Slf4j;

import uk.gov.justice.laa.datauserapi.client.ts.response.ChangeAccountEnabledResponse;
import uk.gov.justice.laa.datauserapi.client.ts.response.ChangeAccountReactivationResponse;
import uk.gov.justice.laa.datauserapi.client.ts.response.TechServicesApiResponse;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;

@Slf4j
public class DoNothingTechServicesClient implements TechServicesClient {

    @Override
    public TechServicesApiResponse<ChangeAccountReactivationResponse> reactivateUser(EntraUserDto user) {
        return TechServicesApiResponse.success(ChangeAccountReactivationResponse.builder().success(true)
                .message("Successfully reactivated user.")
                .build());
    }

    @Override
    public TechServicesApiResponse<ChangeAccountReactivationResponse> deactivateUser(EntraUserDto user, String reason) {
        return TechServicesApiResponse.success(ChangeAccountReactivationResponse.builder().success(true)
                .message("Successfully deactivated user.")
                .build());
    }

    @Override
    public TechServicesApiResponse<ChangeAccountEnabledResponse> updateUserDetails(String entraOid, String firstName, String lastName, String email) {
        return TechServicesApiResponse.success(ChangeAccountEnabledResponse.builder().success(true)
                .message("Successfully Update user details.")
                .build());
    }
    @Override
    public void deleteRoleAssignment(EntraUserDto entraUser) {

    }

}

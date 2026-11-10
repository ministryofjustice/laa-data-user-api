package uk.gov.justice.laa.datauserapi.application.command.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.datauserapi.client.ts.TechServicesClient;
import uk.gov.justice.laa.datauserapi.client.ts.response.ChangeAccountReactivationResponse;
import uk.gov.justice.laa.datauserapi.client.ts.response.TechServicesApiResponse;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeleteUserTechServicesServiceTest {

    @Mock
    private TechServicesClient techServicesClient;

    @Test
    void deactivateUserAndDeleteRoleAssignment_continuesWhenRoleAssignmentDeletionThrows() {
        DeleteUserTechServicesService service = new DeleteUserTechServicesService(techServicesClient);
        EntraUserDto user = new EntraUserDto();
        when(techServicesClient.deactivateUser(user, "RoleChangeorNoLongerRequired"))
                .thenReturn(TechServicesApiResponse.success(ChangeAccountReactivationResponse.builder().build()));
        doThrow(new RuntimeException("Tech Services unavailable"))
                .when(techServicesClient).deleteRoleAssignment(user);

        service.deactivateUserAndDeleteRoleAssignment(user);

        verify(techServicesClient).deactivateUser(user, "RoleChangeorNoLongerRequired");
        verify(techServicesClient).deleteRoleAssignment(user);
    }
}

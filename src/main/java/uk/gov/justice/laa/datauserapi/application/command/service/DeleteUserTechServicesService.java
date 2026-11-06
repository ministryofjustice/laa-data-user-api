package uk.gov.justice.laa.datauserapi.application.command.service;

import static net.logstash.logback.argument.StructuredArguments.kv;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.datauserapi.client.ts.TechServicesClient;
import uk.gov.justice.laa.datauserapi.client.ts.response.ChangeAccountReactivationResponse;
import uk.gov.justice.laa.datauserapi.client.ts.response.TechServicesApiResponse;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;

@Slf4j
@Service
public class DeleteUserTechServicesService {

    private final TechServicesClient techServicesClient;

    public DeleteUserTechServicesService(TechServicesClient techServicesClient) {
        this.techServicesClient = techServicesClient;
    }

    /** Tech Services failures are logged only, so the local delete always proceeds. */
    public void deactivateUserAndDeleteRoleAssignment(EntraUserDto userDto) {
        TechServicesApiResponse<ChangeAccountReactivationResponse> deactivation =
                techServicesClient.deactivateUser(userDto, "RoleChangeorNoLongerRequired");
        if (!deactivation.isSuccess()) {
            log.warn("Failed to disable user in Tech Services; continuing with local deletion",
                    kv("entraOid", userDto.getEntraOid()),
                    kv("techServicesError", deactivation.getError()));
        }

        try {
            techServicesClient.deleteRoleAssignment(userDto);
        } catch (Exception ex) {
            log.warn("Failed to delete role assignment in Tech Services; continuing with local deletion",
                    kv("entraOid", userDto.getEntraOid()),
                    kv("exceptionMessage", ex.getMessage()),
                    ex);
        }
    }
}

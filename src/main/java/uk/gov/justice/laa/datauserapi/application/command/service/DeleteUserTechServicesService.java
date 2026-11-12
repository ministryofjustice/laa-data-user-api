package uk.gov.justice.laa.datauserapi.application.command.service;

import static net.logstash.logback.argument.StructuredArguments.kv;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.datauserapi.client.ts.TechServicesClient;
import uk.gov.justice.laa.datauserapi.client.ts.response.ChangeAccountReactivationResponse;
import uk.gov.justice.laa.datauserapi.client.ts.response.TechServicesApiResponse;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.exception.TechServicesClientException;

@Slf4j
@Service
public class DeleteUserTechServicesService {

    private final TechServicesClient techServicesClient;

    public DeleteUserTechServicesService(TechServicesClient techServicesClient) {
        this.techServicesClient = techServicesClient;
    }

    /** A rejected deactivation or failed role cleanup does not prevent local deletion. */
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
        } catch (TechServicesClientException ex) {
            log.warn("Failed to delete role assignment in Tech Services; continuing with local deletion",
                    kv("entraOid", userDto.getEntraOid()),
                    kv("reason", ex.getReason()),
                    kv("exceptionMessage", ex.getMessage()),
                    ex);
        }
    }
}

package uk.gov.justice.laa.datauserapi.client.ts;

import static net.logstash.logback.argument.StructuredArguments.kv;

import lombok.extern.slf4j.Slf4j;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.exception.TechServicesClientException;

@Slf4j
public class UserCommandValidator {
    public void validateUser(EntraUserDto user, String operation) {
        validateEntraOid(user == null ? null : user.getEntraOid(), operation);
    }

    public void validateEntraOid(String entraOid, String operation) {
        if (entraOid == null || entraOid.isBlank()) {
            log.error("Invalid user details for Tech Services request",
                    kv("entraOid", entraOid), kv("operation", operation));
            throw new TechServicesClientException(
                    "Invalid user details provided for " + operation + " request to Tech Services: Entra OID is required.");
        }
    }
}

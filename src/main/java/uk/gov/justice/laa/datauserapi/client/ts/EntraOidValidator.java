package uk.gov.justice.laa.datauserapi.client.ts;

import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.exception.MissingEntraOidException;

public class EntraOidValidator {
    public void validateUser(EntraUserDto user, String operation) {
        validateEntraOid(user == null ? null : user.getEntraOid(), operation);
    }

    public void validateEntraOid(String entraOid, String operation) {
        if (entraOid == null || entraOid.isBlank()) {
            throw new MissingEntraOidException(operation);
        }
    }
}

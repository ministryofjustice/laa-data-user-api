package uk.gov.justice.laa.datauserapi.exception;

import uk.gov.justice.laa.datauserapi.logging.ExceptionGroup;

public class MissingEntraOidException extends RuntimeException implements ExceptionGroup {
    @Override
    public String groupName() {
        return "missing_entra_oid";
    }

    public MissingEntraOidException(String operation) {
        super("Entra OID is required for " + operation + ".");
    }
}

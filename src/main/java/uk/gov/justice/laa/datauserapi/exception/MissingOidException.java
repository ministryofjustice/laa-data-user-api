package uk.gov.justice.laa.datauserapi.exception;

import uk.gov.justice.laa.datauserapi.logging.ExceptionGroup;

public class MissingOidException extends RuntimeException implements ExceptionGroup {
    @Override
    public String groupName() {
        return "missing_oid";
    }

    public MissingOidException() {
        super("Authenticated user is missing the required OID claim.");
    }
}

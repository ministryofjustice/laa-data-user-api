package uk.gov.justice.laa.datauserapi.exception;

public class MissingOidException extends RuntimeException {
    public MissingOidException() {
        super("Authenticated user is missing the required OID claim.");
    }
}

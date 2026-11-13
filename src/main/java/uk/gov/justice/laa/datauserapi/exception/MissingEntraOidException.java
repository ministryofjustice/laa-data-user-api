package uk.gov.justice.laa.datauserapi.exception;

public class MissingEntraOidException extends RuntimeException {
    public MissingEntraOidException(String operation) {
        super("Entra OID is required for " + operation + ".");
    }
}

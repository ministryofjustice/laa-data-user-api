package uk.gov.justice.laa.datauserapi.exception;

public class OpaUnavailableException extends RuntimeException {

    public OpaUnavailableException(String message) {
        super(message);
    }

    public OpaUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

package uk.gov.justice.laa.datauserapi.exception;

public class ScopeMissingException extends RuntimeException {

    public ScopeMissingException(String message) {
        super(message);
    }

    public ScopeMissingException(String message, Throwable cause) {
        super(message, cause);
    }
}

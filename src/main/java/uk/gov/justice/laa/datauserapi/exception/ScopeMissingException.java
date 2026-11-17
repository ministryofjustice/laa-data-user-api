package uk.gov.justice.laa.datauserapi.exception;

public class ScopeMissingException extends RuntimeException implements ExceptionGroup {

    @Override
    public String groupName() {
        return "scope_missing";
    }

    public ScopeMissingException(String message) {
        super(message);
    }

    public ScopeMissingException(String message, Throwable cause) {
        super(message, cause);
    }
}

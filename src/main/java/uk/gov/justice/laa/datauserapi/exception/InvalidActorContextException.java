package uk.gov.justice.laa.datauserapi.exception;

public class InvalidActorContextException extends RuntimeException {

    public InvalidActorContextException(String message) {
        super(message);
    }

    public InvalidActorContextException(String message, Throwable cause) {
        super(message, cause);
    }
}

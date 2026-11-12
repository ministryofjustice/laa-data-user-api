package uk.gov.justice.laa.datauserapi.exception;

public class InvalidRequestException extends IllegalArgumentException {

    public enum Reason {
        INVALID_DEACTIVATION_REASON,
        INVALID_FIRM_SORT
    }

    private final Reason reason;

    public InvalidRequestException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public InvalidRequestException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}

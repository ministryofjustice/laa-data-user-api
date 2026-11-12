package uk.gov.justice.laa.datauserapi.exception;

public class CcmsNotificationException extends RuntimeException {

    public enum Reason {
        SERIALIZATION_FAILURE,
        INVALID_PROFILE
    }

    private final Reason reason;

    public CcmsNotificationException(String message, Throwable cause) {
        this(Reason.SERIALIZATION_FAILURE, message, cause);
    }

    public CcmsNotificationException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}

package uk.gov.justice.laa.datauserapi.exception;

public class CcmsNotificationException extends RuntimeException
        implements LaaDataApiException<CcmsNotificationException.Reason> {

    public enum Reason {
        SERIALIZATION_FAILURE,
        INVALID_PROFILE
    }

    private final Reason reason;

    @Override
    public String groupName() {
        return "ccms_notification";
    }

    public CcmsNotificationException(String message, Throwable cause) {
        this(Reason.SERIALIZATION_FAILURE, message, cause);
    }

    public CcmsNotificationException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = java.util.Objects.requireNonNull(reason, "reason");
    }

    @Override
    public Reason reason() {
        return reason;
    }
}

package uk.gov.justice.laa.datauserapi.exception;

public class InvalidRequestException extends IllegalArgumentException
        implements LaaDataApiException<InvalidRequestException.Reason> {

    public enum Reason {
        INVALID_DEACTIVATION_REASON,
        INVALID_FIRM_SORT
    }

    private final Reason reason;

    @Override
    public String groupName() {
        return "invalid_request";
    }

    public InvalidRequestException(Reason reason, String message) {
        super(message);
        this.reason = java.util.Objects.requireNonNull(reason, "reason");
    }

    public InvalidRequestException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = java.util.Objects.requireNonNull(reason, "reason");
    }

    @Override
    public Reason reason() {
        return reason;
    }
}

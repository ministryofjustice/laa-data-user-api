package uk.gov.justice.laa.datauserapi.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class CcmsSyncException extends RuntimeException implements LaaDataApiException<CcmsSyncException.Reason> {

    public enum Reason {
        DOWNSTREAM_TIMEOUT,
        DOWNSTREAM_UNAVAILABLE,
        DOWNSTREAM_REJECTED,
        INVALID_RESPONSE,
        MESSAGE_PREPARATION_FAILURE,
        INVALID_PROFILE,
        NO_SYNC_ROLES,
        DOWNSTREAM_FAILURE
    }

    private final HttpStatus status;
    private final Reason reason;

    public CcmsSyncException(HttpStatus status, String message) {
        this(status, reasonFor(status), message, null);
    }

    public CcmsSyncException(HttpStatus status, String message, Throwable cause) {
        this(status, reasonFor(status), message, cause);
    }

    public CcmsSyncException(HttpStatus status, Reason reason, String message) {
        this(status, reason, message, null);
    }

    public CcmsSyncException(HttpStatus status, Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.reason = java.util.Objects.requireNonNull(reason, "reason");
    }

    @Override
    public String groupName() {
        return "ccms_sync";
    }

    @Override
    public Reason reason() {
        return reason;
    }

    private static Reason reasonFor(HttpStatus status) {
        return switch (status) {
            case GATEWAY_TIMEOUT, REQUEST_TIMEOUT -> Reason.DOWNSTREAM_TIMEOUT;
            case SERVICE_UNAVAILABLE -> Reason.DOWNSTREAM_UNAVAILABLE;
            case BAD_GATEWAY -> Reason.DOWNSTREAM_REJECTED;
            default -> Reason.DOWNSTREAM_FAILURE;
        };
    }
}

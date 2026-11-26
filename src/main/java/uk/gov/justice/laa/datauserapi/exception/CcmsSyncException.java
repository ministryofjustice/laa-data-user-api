package uk.gov.justice.laa.datauserapi.exception;

import java.util.Objects;

public class CcmsSyncException extends RuntimeException implements LaaDataApiException<CcmsSyncException.Reason> {
    public enum Reason {
        DOWNSTREAM_TIMEOUT("CCMS SQS request timed out."),
        DOWNSTREAM_UNAVAILABLE("CCMS SQS queue or client is unavailable."),
        DOWNSTREAM_REJECTED("CCMS SQS rejected the synchronisation request."),
        INVALID_RESPONSE("CCMS SQS returned an unexpected response."),
        MESSAGE_PREPARATION_FAILURE("CCMS synchronisation message could not be prepared."),
        INVALID_PROFILE("CCMS profile is missing required data."),
        NO_SYNC_ROLES("No legacy-sync roles remain for the CCMS profile.");

        private final String message;

        Reason(String message) {
            this.message = message;
        }
    }

    private final Reason reason;

    public CcmsSyncException(Reason reason) {
        this(reason, null);
    }

    public CcmsSyncException(Reason reason, Throwable cause) {
        super(Objects.requireNonNull(reason, "reason").message, cause);
        this.reason = reason;
    }

    @Override
    public String groupName() {
        return "ccms_sync";
    }

    @Override
    public Reason reason() {
        return reason;
    }
}

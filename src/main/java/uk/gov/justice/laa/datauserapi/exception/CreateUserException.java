package uk.gov.justice.laa.datauserapi.exception;

import org.springframework.http.HttpStatus;

public class CreateUserException extends RuntimeException implements LaaDataApiException<CreateUserException.Reason> {
    public enum Reason {
        INVALID_REQUEST,
        IDENTITY_ALREADY_EXISTS,
        IDENTITY_PROVIDER_FAILURE,
        LOCAL_PERSISTENCE_FAILURE
    }

    private final HttpStatus status;
    private final String field;
    private final Reason reason;

    public CreateUserException(HttpStatus status, String message, String field) {
        super(message);
        this.status = status;
        this.field = field;
        this.reason = reasonFor(status, field);
    }

    public String field() {
        return field;
    }

    public CreateUserException(HttpStatus status, String message) {
        super(message);
        this.status = status;
        this.field = null;
        this.reason = reasonFor(status, null);
    }

    public CreateUserException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.field = null;
        this.reason = reasonFor(status, null);
    }

    public HttpStatus status() {
        return status;
    }

    @Override
    public String groupName() {
        return "user_creation";
    }

    @Override
    public Reason reason() {
        return reason;
    }

    private static Reason reasonFor(HttpStatus status, String field) {
        if (field != null || status == HttpStatus.BAD_REQUEST) {
            return Reason.INVALID_REQUEST;
        }
        if (status == HttpStatus.CONFLICT) {
            return Reason.IDENTITY_ALREADY_EXISTS;
        }
        if (status.is5xxServerError() && status != HttpStatus.BAD_GATEWAY
                && status != HttpStatus.SERVICE_UNAVAILABLE && status != HttpStatus.GATEWAY_TIMEOUT) {
            return Reason.LOCAL_PERSISTENCE_FAILURE;
        }
        return Reason.IDENTITY_PROVIDER_FAILURE;
    }
}

package uk.gov.justice.laa.datauserapi.exception;

import org.springframework.http.HttpStatus;

import java.util.Objects;

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

    public CreateUserException(Reason reason, HttpStatus status, String message, String field) {
        super(message);
        this.status = Objects.requireNonNull(status, "status");
        this.field = field;
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public String field() {
        return field;
    }

    public CreateUserException(Reason reason, HttpStatus status, String message) {
        this(reason, status, message, (String) null);
    }

    public CreateUserException(Reason reason, HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = Objects.requireNonNull(status, "status");
        this.field = null;
        this.reason = Objects.requireNonNull(reason, "reason");
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
}

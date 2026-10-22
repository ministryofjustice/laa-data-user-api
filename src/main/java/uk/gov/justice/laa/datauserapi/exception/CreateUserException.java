package uk.gov.justice.laa.datauserapi.exception;

import org.springframework.http.HttpStatus;

public class CreateUserException extends RuntimeException {
    private final HttpStatus status;
    private final String field;

    public CreateUserException(HttpStatus status, String message, String field) {
        super(message);
        this.status = status;
        this.field = field;
    }

    public String field() {
        return field;
    }

    public CreateUserException(HttpStatus status, String message) {
        super(message);
        this.status = status;
        this.field = null;
    }

    public CreateUserException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.field = null;
    }

    public HttpStatus status() {
        return status;
    }
}

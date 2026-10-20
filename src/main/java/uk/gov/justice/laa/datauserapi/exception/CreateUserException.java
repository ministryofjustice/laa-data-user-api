package uk.gov.justice.laa.datauserapi.exception;

import org.springframework.http.HttpStatus;

public class CreateUserException extends RuntimeException {
    private final HttpStatus status;

    public CreateUserException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public CreateUserException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}

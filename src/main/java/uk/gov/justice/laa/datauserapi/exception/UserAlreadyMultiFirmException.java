package uk.gov.justice.laa.datauserapi.exception;

public class UserAlreadyMultiFirmException extends RuntimeException {
    public UserAlreadyMultiFirmException(String message) {
        super(message);
    }
}

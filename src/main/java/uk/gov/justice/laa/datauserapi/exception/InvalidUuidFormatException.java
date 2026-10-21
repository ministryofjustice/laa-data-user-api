package uk.gov.justice.laa.datauserapi.exception;

public class InvalidUuidFormatException extends IllegalArgumentException {
    public InvalidUuidFormatException(String message) {
        super(message);
    }

    public InvalidUuidFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}

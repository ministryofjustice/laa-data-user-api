package uk.gov.justice.laa.datauserapi.exception;

public class BadRequestException extends RuntimeException {

    public BadRequestException(String message, Object... args) {
        super(String.format(message, args));
    }
}

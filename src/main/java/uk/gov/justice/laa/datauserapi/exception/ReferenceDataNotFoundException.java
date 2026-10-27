package uk.gov.justice.laa.datauserapi.exception;

public class ReferenceDataNotFoundException extends RuntimeException {
    public ReferenceDataNotFoundException(String message) {
        super(message);
    }
}

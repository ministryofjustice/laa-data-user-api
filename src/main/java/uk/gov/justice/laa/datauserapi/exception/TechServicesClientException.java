package uk.gov.justice.laa.datauserapi.exception;

public class TechServicesClientException extends RuntimeException {

    public TechServicesClientException(String message) {
        super(message);
    }

    public TechServicesClientException(String message, Throwable cause) {
        super(message, cause);
    }
}

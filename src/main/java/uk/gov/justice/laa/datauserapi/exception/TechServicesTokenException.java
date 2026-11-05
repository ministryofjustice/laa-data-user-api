package uk.gov.justice.laa.datauserapi.exception;

public class TechServicesTokenException extends TechServicesClientException {
    public TechServicesTokenException(String message) {
        super(message);
    }

    public TechServicesTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}

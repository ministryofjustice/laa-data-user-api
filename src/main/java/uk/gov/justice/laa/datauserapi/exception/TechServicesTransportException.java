package uk.gov.justice.laa.datauserapi.exception;

public class TechServicesTransportException extends TechServicesClientException {
    public TechServicesTransportException(String message, Throwable cause) {
        super(message, cause);
    }
}

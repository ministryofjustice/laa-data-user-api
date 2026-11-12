package uk.gov.justice.laa.datauserapi.exception;

public class TechServicesResponseMappingException extends TechServicesClientException {

    public TechServicesResponseMappingException(String message) {
        super(message);
    }

    public TechServicesResponseMappingException(String message, Throwable cause) {
        super(message, cause);
    }
}

package uk.gov.justice.laa.datauserapi.exception;

public class TechServicesTransportException extends TechServicesClientException {
    @Override
    public String groupName() {
        return "tech_services_transport";
    }

    public TechServicesTransportException(String message, Throwable cause) {
        super(message, cause);
    }
}

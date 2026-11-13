package uk.gov.justice.laa.datauserapi.exception;

public class TechServicesResponseMappingException extends TechServicesClientException {

    @Override
    public String groupName() {
        return "tech_services_response_mapping";
    }

    public TechServicesResponseMappingException(String message) {
        super(Reason.INVALID_RESPONSE, message);
    }

    public TechServicesResponseMappingException(String message, Throwable cause) {
        super(Reason.INVALID_RESPONSE, message, cause);
    }
}

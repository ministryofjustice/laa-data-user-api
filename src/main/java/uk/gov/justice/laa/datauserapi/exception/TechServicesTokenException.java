package uk.gov.justice.laa.datauserapi.exception;

public class TechServicesTokenException extends TechServicesClientException {
    @Override
    public String groupName() {
        return "tech_services_token";
    }

    public TechServicesTokenException(String message) {
        super(Reason.AUTHENTICATION_FAILURE, message);
    }

    public TechServicesTokenException(String message, Throwable cause) {
        super(Reason.AUTHENTICATION_FAILURE, message, cause);
    }
}

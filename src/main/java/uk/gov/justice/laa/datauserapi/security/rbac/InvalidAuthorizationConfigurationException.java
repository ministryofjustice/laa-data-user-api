package uk.gov.justice.laa.datauserapi.security.rbac;

public class InvalidAuthorizationConfigurationException extends IllegalStateException {

    public InvalidAuthorizationConfigurationException(String message) {
        super(message);
    }
}

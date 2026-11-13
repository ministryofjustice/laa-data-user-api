package uk.gov.justice.laa.datauserapi.security.rbac;

import uk.gov.justice.laa.datauserapi.logging.ExceptionGroup;

public class InvalidAuthorizationConfigurationException extends IllegalStateException implements ExceptionGroup {

    @Override
    public String groupName() {
        return "invalid_authorization_configuration";
    }

    public InvalidAuthorizationConfigurationException(String message) {
        super(message);
    }
}

package uk.gov.justice.laa.datauserapi.exception;

import uk.gov.justice.laa.datauserapi.logging.ExceptionGroup;

public class JwtConfigurationException extends IllegalStateException implements ExceptionGroup {

    @Override
    public String groupName() {
        return "jwt_configuration";
    }

    public JwtConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}

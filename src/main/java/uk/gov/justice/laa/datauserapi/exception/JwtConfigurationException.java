package uk.gov.justice.laa.datauserapi.exception;

public class JwtConfigurationException extends IllegalStateException implements ExceptionGroup {

    @Override
    public String groupName() {
        return "jwt_configuration";
    }

    public JwtConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}

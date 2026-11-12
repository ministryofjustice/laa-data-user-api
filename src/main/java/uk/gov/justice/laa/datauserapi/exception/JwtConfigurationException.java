package uk.gov.justice.laa.datauserapi.exception;

public class JwtConfigurationException extends IllegalStateException {

    public JwtConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}

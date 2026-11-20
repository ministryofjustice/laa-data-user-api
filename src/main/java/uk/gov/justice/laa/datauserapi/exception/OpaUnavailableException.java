package uk.gov.justice.laa.datauserapi.exception;

public class OpaUnavailableException extends RuntimeException implements ExceptionGroup {

    @Override
    public String groupName() {
        return "opa_unavailable";
    }

    public OpaUnavailableException(String message) {
        super(message);
    }

    public OpaUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

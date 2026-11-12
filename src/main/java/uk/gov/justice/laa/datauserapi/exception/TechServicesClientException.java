package uk.gov.justice.laa.datauserapi.exception;

import lombok.Getter;
import org.springframework.http.ResponseEntity;

@Getter
public class TechServicesClientException extends RuntimeException {

    public enum Reason {
        DOWNSTREAM_FAILURE,
        INVALID_RESPONSE,
        AUTHENTICATION_FAILURE,
        CONFIGURATION_FAILURE,
        INVALID_REQUEST
    }

    private Reason reason = Reason.DOWNSTREAM_FAILURE;
    private String code;
    private String[] errors;
    private ResponseEntity<?> responseEntity;

    public TechServicesClientException(String message) {
        super(message);
    }

    public TechServicesClientException(String message, Throwable cause) {
        super(message, cause);
    }

    public TechServicesClientException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public TechServicesClientException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public TechServicesClientException(String message, String... args) {
        super(String.format(message, (Object[]) args));
    }

    public TechServicesClientException(String message, String code) {
        super(message);
        this.code = code;
    }

    public TechServicesClientException(String message, String code, String... errors) {
        super(message);
        this.code = code;
        this.errors = errors;
    }

    public TechServicesClientException(ResponseEntity<?> responseEntity) {
        super("There was an error when sending a request to Tech Services.");
        this.responseEntity = responseEntity;
    }

}

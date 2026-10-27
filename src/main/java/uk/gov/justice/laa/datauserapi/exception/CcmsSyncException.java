package uk.gov.justice.laa.datauserapi.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class CcmsSyncException extends RuntimeException {

    private final HttpStatus status;

    public CcmsSyncException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public CcmsSyncException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }
}

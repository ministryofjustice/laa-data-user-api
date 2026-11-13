package uk.gov.justice.laa.datauserapi.exception;

import uk.gov.justice.laa.datauserapi.logging.ExceptionGroup;

public class InvalidUuidFormatException extends IllegalArgumentException implements ExceptionGroup {
    @Override
    public String groupName() {
        return "invalid_uuid_format";
    }

    public InvalidUuidFormatException(String message) {
        super(message);
    }

    public InvalidUuidFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}

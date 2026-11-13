package uk.gov.justice.laa.datauserapi.exception;

import uk.gov.justice.laa.datauserapi.logging.ExceptionGroup;

public class ResourceNotFoundException extends RuntimeException implements ExceptionGroup {
    @Override
    public String groupName() {
        return "resource_not_found";
    }

    public ResourceNotFoundException(String message) {
        super(message);
    }
}

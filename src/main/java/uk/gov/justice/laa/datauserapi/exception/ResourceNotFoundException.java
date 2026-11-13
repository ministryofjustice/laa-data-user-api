package uk.gov.justice.laa.datauserapi.exception;

public class ResourceNotFoundException extends RuntimeException implements ExceptionGroup {
    @Override
    public String groupName() {
        return "resource_not_found";
    }

    public ResourceNotFoundException(String message) {
        super(message);
    }
}

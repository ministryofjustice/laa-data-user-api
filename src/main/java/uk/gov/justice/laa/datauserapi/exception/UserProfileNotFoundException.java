package uk.gov.justice.laa.datauserapi.exception;

public class UserProfileNotFoundException extends ResourceNotFoundException {
    public UserProfileNotFoundException(String message) {
        super(message);
    }
}

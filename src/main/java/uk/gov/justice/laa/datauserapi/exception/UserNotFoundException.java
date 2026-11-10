package uk.gov.justice.laa.datauserapi.exception;

import java.util.UUID;

public class UserNotFoundException extends ResourceNotFoundException {

    public UserNotFoundException(UUID userId) {
        this(userId.toString());
    }

    public UserNotFoundException(String userId) {
        super("User not found with ID: " + userId);
    }
}

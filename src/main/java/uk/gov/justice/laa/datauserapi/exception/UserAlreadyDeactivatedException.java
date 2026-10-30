package uk.gov.justice.laa.datauserapi.exception;

import java.util.UUID;

public class UserAlreadyDeactivatedException extends RuntimeException {

    public UserAlreadyDeactivatedException(UUID userId) {
        super("User is already deactivated: " + userId);
    }
}

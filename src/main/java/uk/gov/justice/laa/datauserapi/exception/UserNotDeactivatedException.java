package uk.gov.justice.laa.datauserapi.exception;

import java.util.UUID;

public class UserNotDeactivatedException extends RuntimeException {

    public UserNotDeactivatedException(UUID userId) {
        super("User is not deactivated: " + userId);
    }
}

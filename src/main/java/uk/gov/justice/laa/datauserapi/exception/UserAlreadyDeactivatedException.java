package uk.gov.justice.laa.datauserapi.exception;

import java.util.UUID;

public class UserAlreadyDeactivatedException extends RuntimeException implements ExceptionGroup {

    @Override
    public String groupName() {
        return "user_already_deactivated";
    }

    public UserAlreadyDeactivatedException(UUID userId) {
        super("User is already deactivated: " + userId);
    }
}

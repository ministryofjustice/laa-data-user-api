package uk.gov.justice.laa.datauserapi.exception;

import java.util.UUID;

public class UserNotDeactivatedException extends RuntimeException implements ExceptionGroup {

    @Override
    public String groupName() {
        return "user_not_deactivated";
    }

    public UserNotDeactivatedException(UUID userId) {
        super("User is not deactivated: " + userId);
    }
}

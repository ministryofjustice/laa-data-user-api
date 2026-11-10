package uk.gov.justice.laa.datauserapi.exception;

import java.util.UUID;

public class UserProfileNotFoundException extends ResourceNotFoundException {
    public UserProfileNotFoundException(UUID profileId) {
        super("User profile not found with ID: " + profileId);
    }

    private UserProfileNotFoundException(String userId) {
        super("Active profile not found for user: " + userId);
    }

    public static UserProfileNotFoundException activeProfileForUser(String userId) {
        return new UserProfileNotFoundException(userId);
    }
}

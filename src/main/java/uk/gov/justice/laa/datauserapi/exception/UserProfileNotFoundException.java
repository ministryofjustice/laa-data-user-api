package uk.gov.justice.laa.datauserapi.exception;

import java.util.UUID;

public class UserProfileNotFoundException extends ResourceNotFoundException
        implements LaaDataApiException<UserProfileNotFoundReason> {
    private final UserProfileNotFoundReason reason;

    @Override
    public String groupName() {
        return "user_profile_not_found";
    }

    public UserProfileNotFoundException(UUID profileId) {
        super("User profile not found with ID: " + profileId);
        this.reason = UserProfileNotFoundReason.PROFILE_ID_NOT_FOUND;
    }

    private UserProfileNotFoundException(String userId) {
        super("Active profile not found for user: " + userId);
        this.reason = UserProfileNotFoundReason.ACTIVE_PROFILE_NOT_FOUND;
    }

    @Override
    public UserProfileNotFoundReason reason() {
        return reason;
    }

    public static UserProfileNotFoundException activeProfileForUser(String userId) {
        return new UserProfileNotFoundException(userId);
    }
}

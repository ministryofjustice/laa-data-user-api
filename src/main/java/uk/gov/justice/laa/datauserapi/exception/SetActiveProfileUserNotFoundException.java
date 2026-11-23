package uk.gov.justice.laa.datauserapi.exception;

public class SetActiveProfileUserNotFoundException extends ResourceNotFoundException {

    @Override
    public String groupName() {
        return "set_active_profile_user_not_found";
    }

    public SetActiveProfileUserNotFoundException() {
        super("User not found while setting active profile");
    }
}

package uk.gov.justice.laa.datauserapi.exception;

public class SetActiveProfileNotFoundException extends ResourceNotFoundException {

    @Override
    public String groupName() {
        return "set_active_profile_not_found";
    }

    public SetActiveProfileNotFoundException() {
        super("Profile not found while setting active profile");
    }
}

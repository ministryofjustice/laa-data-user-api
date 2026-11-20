package uk.gov.justice.laa.datauserapi.exception;

import java.util.UUID;

/** The app role does not exist or belongs to an inactive application, so it is not part of the catalogue. */
public class AppRoleNotFoundException extends ResourceNotFoundException {

    @Override
    public String groupName() {
        return "app_role_not_found";
    }

    public AppRoleNotFoundException(UUID appRoleId) {
        super("Eligible app role not found with ID: " + appRoleId);
    }
}

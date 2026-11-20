package uk.gov.justice.laa.datauserapi.exception;

import java.util.UUID;

/** The application does not exist or is inactive, so it is not part of the catalogue. */
public class AppNotFoundException extends ResourceNotFoundException {

    @Override
    public String groupName() {
        return "app_not_found";
    }

    public AppNotFoundException(UUID appId) {
        super("Active app not found with ID: " + appId);
    }
}

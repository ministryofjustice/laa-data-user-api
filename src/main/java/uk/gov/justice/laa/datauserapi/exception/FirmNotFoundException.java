package uk.gov.justice.laa.datauserapi.exception;

public class FirmNotFoundException extends ResourceNotFoundException {

    public static final String CLIENT_DETAIL = "Firm not found";

    @Override
    public String groupName() {
        return "firm_not_found";
    }

    public FirmNotFoundException(String firmId) {
        super("Firm not found with ID: " + firmId);
    }
}

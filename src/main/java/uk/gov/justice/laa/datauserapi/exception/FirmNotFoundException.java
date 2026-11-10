package uk.gov.justice.laa.datauserapi.exception;

public class FirmNotFoundException extends ResourceNotFoundException {

    public static final String CLIENT_DETAIL = "Firm not found";

    public FirmNotFoundException(String firmId) {
        super("Firm not found with ID: " + firmId);
    }
}

package uk.gov.justice.laa.datauserapi.exception;

public class ReferenceDataNotFoundException extends RuntimeException implements ExceptionGroup {
    @Override
    public String groupName() {
        return "reference_data_not_found";
    }

    public ReferenceDataNotFoundException(String message) {
        super(message);
    }
}

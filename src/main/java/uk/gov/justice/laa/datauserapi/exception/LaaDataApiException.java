package uk.gov.justice.laa.datauserapi.exception;

public interface LaaDataApiException<R extends Enum<R>> extends ExceptionGroup {

    R reason();
}

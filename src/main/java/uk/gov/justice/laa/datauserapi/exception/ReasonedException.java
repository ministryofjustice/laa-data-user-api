package uk.gov.justice.laa.datauserapi.exception;

public interface ReasonedException<R extends Enum<R>> extends ExceptionGroup {

    R reason();
}

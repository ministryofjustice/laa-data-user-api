package uk.gov.justice.laa.datauserapi.exception;

import uk.gov.justice.laa.datauserapi.logging.ExceptionGroup;

public interface LaaDataApiException<R extends Enum<R>> extends ExceptionGroup {

    R reason();
}

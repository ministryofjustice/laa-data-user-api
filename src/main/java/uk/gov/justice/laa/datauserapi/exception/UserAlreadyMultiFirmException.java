package uk.gov.justice.laa.datauserapi.exception;

import uk.gov.justice.laa.datauserapi.logging.ExceptionGroup;

public class UserAlreadyMultiFirmException extends RuntimeException implements ExceptionGroup {
    @Override
    public String groupName() {
        return "user_already_multi_firm";
    }

    public UserAlreadyMultiFirmException(String message) {
        super(message);
    }
}

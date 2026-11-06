package uk.gov.justice.laa.datauserapi.exception;

import uk.gov.justice.laa.datauserapi.contracts.response.CreateUserAccountResult;

public class UserAlreadyExistsException extends RuntimeException {
    private final CreateUserAccountResult result;

    public UserAlreadyExistsException(CreateUserAccountResult result) {
        super("A user with this email already exists");
        this.result = result;
    }

    public CreateUserAccountResult result() {
        return result;
    }
}

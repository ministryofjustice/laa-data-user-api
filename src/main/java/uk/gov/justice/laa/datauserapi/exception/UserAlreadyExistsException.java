package uk.gov.justice.laa.datauserapi.exception;

import uk.gov.justice.laa.datauserapi.contracts.response.CreateUserAccountResponse;

public class UserAlreadyExistsException extends RuntimeException implements ExceptionGroup {
    private final CreateUserAccountResponse result;

    public UserAlreadyExistsException(CreateUserAccountResponse result) {
        super("A user with this email already exists");
        this.result = result;
    }

    public CreateUserAccountResponse result() {
        return result;
    }

    @Override
    public String groupName() {
        return "user_already_exists";
    }
}

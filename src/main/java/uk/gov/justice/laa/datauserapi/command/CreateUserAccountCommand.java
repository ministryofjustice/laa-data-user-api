package uk.gov.justice.laa.datauserapi.command;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUserAccountCommand(
        @NotBlank @Email String email,
        @NotBlank @Size(max = 255) String firstName,
        @NotBlank @Size(max = 255) String lastName,
        @NotNull UserType userType,
        @NotNull Boolean multiFirmUser,
        String firmId
) {
    @AssertTrue(message = "userType must be EXTERNAL and firmId must be provided")
    public boolean isExternalFirmUser() {
        return userType == UserType.EXTERNAL && firmId != null && !firmId.isBlank();
    }

    public enum UserType {
        INTERNAL, EXTERNAL
    }
}

package uk.gov.justice.laa.datauserapi.contracts.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.util.UUID;

/**
 * Request to update a user's name and/or email. At least one of the details must be provided.
 */
@Builder
public record UpdateUserDetailsRequest(
        @NotNull UUID userEntraObjectId,
        @Size(max = 255) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String firstName,
        @Size(max = 255) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String lastName,
        @Size(max = 255) @Email String email
) {

    @JsonIgnore
    @AssertTrue(message = "At least one of firstName, lastName or email must be provided")
    public boolean isAtLeastOneDetailProvided() {
        return firstName != null || lastName != null || email != null;
    }
}

package uk.gov.justice.laa.datauserapi.exception;

import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserLookupExceptionTest {

    private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void userIdentifiersUseTheSameFixedMessageTemplate() {
        assertThat(new UserNotFoundException(ID)).hasMessage("User not found with ID: " + ID);
        assertThat(new UserNotFoundException(ID.toString())).hasMessage("User not found with ID: " + ID);
    }

    @Test
    void profileAndActiveProfileLookupsUseFixedTemplates() {
        assertThat(new UserProfileNotFoundException(ID)).hasMessage("User profile not found with ID: " + ID);
        assertThat(UserProfileNotFoundException.activeProfileForUser(ID.toString()))
                .hasMessage("Active profile not found for user: " + ID);
    }

    @Test
    void actorContextFactoriesPreserveDiagnosticMessages() {
        assertThat(InvalidActorContextException.noActiveProfile(ID.toString()))
                .hasMessage("Actor has no active profile: " + ID);
        assertThat(InvalidActorContextException.undeterminedUserType())
                .hasMessage("Actor user type cannot be determined");
    }
}

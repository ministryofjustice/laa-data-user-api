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
        assertThat(new UserProfileNotFoundException(ID).reason())
                .isEqualTo(UserProfileNotFoundReason.PROFILE_ID_NOT_FOUND);
        assertThat(UserProfileNotFoundException.activeProfileForUser(ID.toString()).reason())
                .isEqualTo(UserProfileNotFoundReason.ACTIVE_PROFILE_NOT_FOUND);
    }

    @Test
    void actorContextFactoriesPreserveDiagnosticMessages() {
        assertThat(InvalidActorContextException.noActiveProfile(ID.toString()))
                .hasMessage("Actor has no active profile: " + ID);
        assertThat(InvalidActorContextException.undeterminedUserType())
                .hasMessage("Actor user type cannot be determined");
        assertThat(InvalidActorContextException.noActiveProfile(ID.toString()).reason())
                .isEqualTo(InvalidActorContextReason.NO_ACTIVE_PROFILE);
        assertThat(InvalidActorContextException.undeterminedUserType().reason())
                .isEqualTo(InvalidActorContextReason.UNDETERMINED_USER_TYPE);
    }

    @Test
    void actorIdentityFactoriesExposeTypedReasonsAndPreserveCauses() {
        IllegalArgumentException cause = new IllegalArgumentException("Invalid UUID");

        assertThat(InvalidActorContextException.missingActorIdentity())
                .hasMessage("Authentication token is missing the actor identity")
                .hasNoCause();
        assertThat(InvalidActorContextException.missingActorIdentity().reason())
                .isEqualTo(InvalidActorContextReason.MISSING_ACTOR_IDENTITY);
        assertThat(InvalidActorContextException.invalidActorIdentity(cause))
                .hasMessage("Authentication token contains an invalid actor identity")
                .hasCause(cause);
        assertThat(InvalidActorContextException.invalidActorIdentity(cause).reason())
                .isEqualTo(InvalidActorContextReason.INVALID_ACTOR_IDENTITY);
    }
}

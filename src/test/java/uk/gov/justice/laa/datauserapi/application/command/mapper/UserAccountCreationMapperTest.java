package uk.gov.justice.laa.datauserapi.application.command.mapper;

import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.datauserapi.contracts.request.CreateUserAccountRequest;

import static org.assertj.core.api.Assertions.assertThat;

class UserAccountCreationMapperTest {
    private final UserAccountCreationMapper mapper = new UserAccountCreationMapper();

    @Test
    void mapsRequestToCommandAndNormalizesEmail() {
        var request = new CreateUserAccountRequest(
                "Person@Example.com", "First", "Last", "EXTERNAL", true, "firm-id");

        var command = mapper.toCommand(request);

        assertThat(command.email()).isEqualTo("person@example.com");
        assertThat(command.firstName()).isEqualTo("First");
        assertThat(command.lastName()).isEqualTo("Last");
        assertThat(command.userType()).isEqualTo("EXTERNAL");
        assertThat(command.multiFirmUser()).isTrue();
        assertThat(command.firmId()).isEqualTo("firm-id");
    }
}

package uk.gov.justice.laa.datauserapi.application.command.controller;

import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import uk.gov.justice.laa.datauserapi.contracts.request.UpdateOfficesRequest;
import uk.gov.justice.laa.datauserapi.contracts.request.UpdateOfficesUnrestrictedCommand;
import uk.gov.justice.laa.datauserapi.contracts.request.UpdateUserProfileOfficesRequest;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UpdateOfficesRequestDeserializationTest {

    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @Test
    void deserialize_recognizesOfficeAssignmentsRequest() {
        UUID profileId = UUID.randomUUID();
        UUID officeId = UUID.randomUUID();
        String json = """
                {"userProfileId":"%s","officeIds":["%s"]}
                """.formatted(profileId, officeId);

        UpdateOfficesRequest request = objectMapper.readValue(json, UpdateOfficesRequest.class);

        assertThat(request).isInstanceOf(UpdateUserProfileOfficesRequest.class);
        UpdateUserProfileOfficesRequest officesRequest = (UpdateUserProfileOfficesRequest) request;
        assertThat(officesRequest.userProfileId()).isEqualTo(profileId);
        assertThat(officesRequest.officeIds()).isEqualTo(Set.of(officeId));
    }

    @Test
    void deserialize_recognizesUnrestrictedAccessRequest() {
        UUID profileId = UUID.randomUUID();
        String json = """
                {"userProfileId":"%s","unrestrictedOfficeAccess":true}
                """.formatted(profileId);

        UpdateOfficesRequest request = objectMapper.readValue(json, UpdateOfficesRequest.class);

        assertThat(request).isInstanceOf(UpdateOfficesUnrestrictedCommand.class);
        UpdateOfficesUnrestrictedCommand unrestrictedCommand = (UpdateOfficesUnrestrictedCommand) request;
        assertThat(unrestrictedCommand.userProfileId()).isEqualTo(profileId);
        assertThat(unrestrictedCommand.unrestrictedOfficeAccess()).isTrue();
    }

    @Test
    void deserialize_failsWhenRequestShapeDoesNotMatchEitherCommand() {
        assertThatThrownBy(() -> objectMapper.readValue(
                "{\"userProfileId\":\"" + UUID.randomUUID() + "\"}", UpdateOfficesRequest.class))
                .isInstanceOf(JacksonException.class);
    }
}

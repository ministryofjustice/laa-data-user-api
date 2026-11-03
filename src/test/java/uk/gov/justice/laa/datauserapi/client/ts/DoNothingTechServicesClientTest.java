package uk.gov.justice.laa.datauserapi.client.ts;

import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class DoNothingTechServicesClientTest {

    private final DoNothingTechServicesClient client = new DoNothingTechServicesClient();
    private final EntraUserDto user = new EntraUserDto();

    @Test
    void reactivateReturnsSuccess() {
        assertThat(client.reactivateUser(user).isSuccess()).isTrue();
    }

    @Test
    void deactivateReturnsSuccess() {
        assertThat(client.deactivateUser(user, "reason").isSuccess()).isTrue();
    }

    @Test
    void deleteRoleAssignmentDoesNothing() {
        assertThatCode(() -> client.deleteRoleAssignment(user)).doesNotThrowAnyException();
    }
}

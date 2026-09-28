package uk.gov.justice.laa.datauserapi.client.ts;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.datauserapi.client.ts.response.ChangeAccountReactivationResponse;
import uk.gov.justice.laa.datauserapi.client.ts.response.TechServicesApiResponse;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
public class DoNothingTestServiceClientTest {

    private TechServicesClient techServicesClient;

    @BeforeEach
    public void setup() {
        techServicesClient = new DoNothingTechServicesClient();

    }

    @Test
    void testReactivateUser() {
        EntraUserDto user = EntraUserDto.builder().build();

        TechServicesApiResponse<ChangeAccountReactivationResponse> response = techServicesClient.reactivateUser(user);

        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isNotNull();
        assertThat(response.getData().getMessage()).isEqualTo("Successfully reactivated user.");
    }

    @Test
    void testDeactivateUser() {
        EntraUserDto user = EntraUserDto.builder().build();

        TechServicesApiResponse<ChangeAccountReactivationResponse> response = techServicesClient.deactivateUser(user, "reason");

        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isNotNull();
        assertThat(response.getData().getMessage()).isEqualTo("Successfully deactivated user.");
    }

}

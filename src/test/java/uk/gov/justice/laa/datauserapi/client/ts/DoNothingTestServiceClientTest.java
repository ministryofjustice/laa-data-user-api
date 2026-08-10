package uk.gov.justice.laa.datauserapi.client.ts;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.datauserapi.client.ts.response.ChangeAccountEnabledResponse;
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
    void testEnableUser() {
        EntraUserDto user = EntraUserDto.builder().build();

        TechServicesApiResponse<ChangeAccountEnabledResponse> response = techServicesClient.enableUser(user);

        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isNotNull();
        assertThat(response.getData().getMessage()).isEqualTo("Successfully enabled user.");
    }

    @Test
    void testDisableUser() {
        EntraUserDto user = EntraUserDto.builder().build();

        TechServicesApiResponse<ChangeAccountEnabledResponse> response = techServicesClient.disableUser(user, "reason");

        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isNotNull();
        assertThat(response.getData().getMessage()).isEqualTo("Successfully disabled user.");
    }

}

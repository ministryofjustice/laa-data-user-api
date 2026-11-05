package uk.gov.justice.laa.datauserapi.client.ts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import uk.gov.justice.laa.datauserapi.exception.TechServicesTransportException;

@ExtendWith(MockitoExtension.class)
class RestTechServicesApiTest {
    @Mock
    private RestClient restClient;
    @Mock
    private RestClient.RequestBodyUriSpec uriSpec;
    @Mock
    private RestClient.RequestBodySpec bodySpec;
    @Mock
    private RestClient.ResponseSpec responseSpec;
    private RestTechServicesApi api;

    @BeforeEach
    void setUp() {
        api = new RestTechServicesApi(restClient);
        when(restClient.patch()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.header(anyString(), anyString())).thenReturn(bodySpec);
        when(bodySpec.contentType(org.mockito.ArgumentMatchers.any())).thenReturn(bodySpec);
        when(bodySpec.body(org.mockito.ArgumentMatchers.any(Object.class))).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);
    }

    @Test
    void returnsSuccessfulResponseUnchanged() {
        when(responseSpec.toEntity(String.class)).thenReturn(org.springframework.http.ResponseEntity.ok("{}"));

        assertThat(api.patch("/users/u1", "body", "token").getBody()).isEqualTo("{}");
    }

    @Test
    void turnsHttpStatusExceptionIntoRawResponseForMapping() {
        HttpClientErrorException httpError = HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST, "Bad Request", HttpHeaders.EMPTY, "{\"code\":\"bad\"}".getBytes(), null);
        when(responseSpec.toEntity(String.class)).thenThrow(httpError);

        var response = api.patch("/users/u1", "body", "token");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo("{\"code\":\"bad\"}");
    }

    @Test
    void wrapsTransportFailureInSpecificClientException() {
        when(responseSpec.toEntity(String.class)).thenThrow(new RestClientException("connection failed"));

        assertThatThrownBy(() -> api.patch("/users/u1", "body", "token"))
                .isInstanceOf(TechServicesTransportException.class)
                .hasMessageContaining("communicate with Tech Services")
                .hasCauseInstanceOf(RestClientException.class);
    }
}

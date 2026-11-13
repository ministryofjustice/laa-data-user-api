package uk.gov.justice.laa.datauserapi.client.ts;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import uk.gov.justice.laa.datauserapi.exception.TechServicesTransportException;

public class RestTechServicesApi implements TechServicesApi {
    private final RestClient restClient;

    public RestTechServicesApi(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public ResponseEntity<String> post(String endpoint, Object request, String accessToken) {
        RestClient.ResponseSpec response = restClient.post()
                .uri(endpoint)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve();
        return toResponse(response);
    }

    @Override
    public ResponseEntity<String> patch(String endpoint, Object request, String accessToken) {
        RestClient.ResponseSpec response = restClient.patch()
                .uri(endpoint)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve();
        return toResponse(response);
    }

    private ResponseEntity<String> toResponse(RestClient.ResponseSpec response) {
        try {
            return response.toEntity(String.class);
        } catch (HttpStatusCodeException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                    .headers(ex.getResponseHeaders())
                    .body(ex.getResponseBodyAsString());
        } catch (RestClientException ex) {
            throw new TechServicesTransportException("Unable to communicate with Tech Services.", ex);
        }
    }
}

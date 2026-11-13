package uk.gov.justice.laa.datauserapi.client.ts;

import org.springframework.http.ResponseEntity;

public interface TechServicesApi {
    ResponseEntity<String> patch(String endpoint, Object request, String accessToken);
}

package uk.gov.justice.laa.datauserapi.client.ts;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import uk.gov.justice.laa.datauserapi.client.ts.response.ChangeAccountEnabledResponse;
import uk.gov.justice.laa.datauserapi.client.ts.response.ChangeAccountReactivationResponse;
import uk.gov.justice.laa.datauserapi.client.ts.response.TechServicesApiResponse;
import uk.gov.justice.laa.datauserapi.client.ts.response.TechServicesErrorResponse;
import uk.gov.justice.laa.datauserapi.client.ts.response.UpdateSecurityGroupsResponse;
import uk.gov.justice.laa.datauserapi.exception.TechServicesResponseMappingException;

public class TechServicesResultMapper {
    private final ObjectMapper objectMapper;

    public TechServicesResultMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public TechServicesApiResponse<ChangeAccountReactivationResponse> mapAccountChange(ResponseEntity<String> response) {
        if (response.getStatusCode().is2xxSuccessful()) {
            return TechServicesApiResponse.success(readRequiredBody(response, ChangeAccountReactivationResponse.class));
        }
        return TechServicesApiResponse.error(readRequiredBody(response, TechServicesErrorResponse.class));
    }

    public UpdateSecurityGroupsResponse mapSecurityGroups(ResponseEntity<String> response) {
        return readRequiredBody(response, UpdateSecurityGroupsResponse.class);
    }

    public TechServicesApiResponse<ChangeAccountEnabledResponse> mapUserDetails(ResponseEntity<String> response) {
        if (response.getStatusCode() == HttpStatus.OK && hasBody(response)) {
            return TechServicesApiResponse.success(readRequiredBody(response, ChangeAccountEnabledResponse.class));
        }
        if (response.getStatusCode().is4xxClientError() || response.getStatusCode().is5xxServerError()) {
            if (!hasBody(response)) {
                return TechServicesApiResponse.error(TechServicesErrorResponse.builder()
                        .success(false)
                        .code(response.getStatusCode().toString())
                        .message(statusText(response))
                        .build());
            }
            return TechServicesApiResponse.error(readRequiredBody(response, TechServicesErrorResponse.class));
        }
        return TechServicesApiResponse.error(TechServicesErrorResponse.builder()
                .success(false)
                .code("UNEXPECTED_RESPONSE")
                .message("Unexpected response from Tech Services")
                .build());
    }

    private <T> T readRequiredBody(ResponseEntity<String> response, Class<T> targetType) {
        if (!hasBody(response)) {
            throw new TechServicesResponseMappingException("Tech Services returned an empty response body.");
        }
        T value;
        try {
            value = objectMapper.readValue(response.getBody(), targetType);
        } catch (JsonProcessingException ex) {
            throw new TechServicesResponseMappingException("Could not parse Tech Services response.", ex);
        }
        if (value == null) {
            throw new TechServicesResponseMappingException("Tech Services returned a null response body.");
        }
        return value;
    }

    private boolean hasBody(ResponseEntity<String> response) {
        return response.getBody() != null && !response.getBody().isBlank();
    }

    private String statusText(ResponseEntity<String> response) {
        HttpStatus status = HttpStatus.resolve(response.getStatusCode().value());
        return status == null ? response.getStatusCode().toString() : status.getReasonPhrase();
    }
}

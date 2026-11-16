package uk.gov.justice.laa.datauserapi.client.ts;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import uk.gov.justice.laa.datauserapi.client.ts.response.TechServicesErrorResponse;
import uk.gov.justice.laa.datauserapi.client.ts.response.UpdateSecurityGroupsResponse;
import uk.gov.justice.laa.datauserapi.exception.TechServicesResponseMappingException;

public class TechServicesResultMapper {
    private final ObjectMapper objectMapper;

    public TechServicesResultMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public <T> T mapSuccessBody(String body, Class<T> targetType) {
        return readRequiredBody(body, targetType);
    }

    public TechServicesErrorResponse mapErrorBody(String body) {
        return readRequiredBody(body, TechServicesErrorResponse.class);
    }

    public UpdateSecurityGroupsResponse mapSecurityGroups(String body) {
        return readRequiredBody(body, UpdateSecurityGroupsResponse.class);
    }

    public UUID mapCreatedUserId(String body) {
        if (body == null || body.isBlank()) {
            throw new TechServicesResponseMappingException("Tech Services returned an empty identity response.");
        }

        try {
            JsonNode response = objectMapper.reader()
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(body);
            if (response == null || !response.path("success").isBoolean()
                    || !response.path("success").booleanValue()) {
                throw new TechServicesResponseMappingException("Tech Services returned an invalid identity response.");
            }
            String id = response.path("user").path("id").asText("");
            UUID entraOid = UUID.fromString(id);
            if (!entraOid.toString().equalsIgnoreCase(id)) {
                throw new IllegalArgumentException("Identity ID is not a canonical UUID.");
            }
            return entraOid;
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            throw new TechServicesResponseMappingException("Tech Services returned an invalid identity response.", ex);
        }
    }

    private <T> T readRequiredBody(String body, Class<T> targetType) {
        if (body == null || body.isBlank()) {
            throw new TechServicesResponseMappingException("Tech Services returned an empty response body.");
        }
        T value;
        try {
            value = objectMapper.readValue(body, targetType);
        } catch (JsonProcessingException ex) {
            throw new TechServicesResponseMappingException("Could not parse Tech Services response.", ex);
        }
        if (value == null) {
            throw new TechServicesResponseMappingException("Tech Services returned a null response body.");
        }
        return value;
    }
}

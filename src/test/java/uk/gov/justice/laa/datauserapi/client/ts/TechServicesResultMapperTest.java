package uk.gov.justice.laa.datauserapi.client.ts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.datauserapi.client.ts.response.ChangeAccountReactivationResponse;
import uk.gov.justice.laa.datauserapi.client.ts.response.TechServicesErrorCode;
import uk.gov.justice.laa.datauserapi.exception.TechServicesClientException;
import uk.gov.justice.laa.datauserapi.exception.TechServicesResponseMappingException;

class TechServicesResultMapperTest {
    private TechServicesResultMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new TechServicesResultMapper(new ObjectMapper());
    }

    @Test
    void mapsSuccessPayload() {
        var result = mapper.mapSuccessBody(
                "{\"success\":true,\"message\":\"ok\"}", ChangeAccountReactivationResponse.class);
        assertThat(result.getMessage()).isEqualTo("ok");
    }

    @Test
    void mapsErrorPayload() {
        var result = mapper.mapErrorBody("{\"code\":\"INVALID\",\"message\":\"bad\"}");
        assertThat(result.getCode()).isEqualTo(new TechServicesErrorCode("INVALID"));
    }

    @Test
    void rejectsMissingOrJsonNullRequiredBodies() {
        assertThatThrownBy(() -> mapper.mapSuccessBody(null, ChangeAccountReactivationResponse.class))
                .isInstanceOf(TechServicesClientException.class)
                .hasMessageContaining("empty response body");
        assertThatThrownBy(() -> mapper.mapSuccessBody("null", ChangeAccountReactivationResponse.class))
                .isInstanceOf(TechServicesClientException.class)
                .hasMessageContaining("null response body");
    }

    @Test
    void wrapsMalformedJsonAsClientExceptionWithCause() {
        assertThatThrownBy(() -> mapper.mapSuccessBody("{", ChangeAccountReactivationResponse.class))
                .isInstanceOf(TechServicesResponseMappingException.class)
                .hasMessageContaining("parse Tech Services response")
                .hasCauseInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
    }

    @Test
    void securityGroupSuccessRequiresNonNullBody() {
        assertThatThrownBy(() -> mapper.mapSecurityGroups(null))
                .isInstanceOf(TechServicesClientException.class);
    }
}

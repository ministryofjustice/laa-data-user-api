package uk.gov.justice.laa.datauserapi.client.ts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import uk.gov.justice.laa.datauserapi.exception.TechServicesClientException;
import uk.gov.justice.laa.datauserapi.exception.TechServicesResponseMappingException;

class TechServicesResultMapperTest {
    private TechServicesResultMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new TechServicesResultMapper(new ObjectMapper());
    }

    @Test
    void mapsAny2xxAccountChangeToSuccess() {
        var result = mapper.mapAccountChange(ResponseEntity.status(HttpStatus.ACCEPTED)
                .body("{\"success\":true,\"message\":\"ok\"}"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getMessage()).isEqualTo("ok");
    }

    @Test
    void mapsAccountChangeHttpErrorToErrorDto() {
        var result = mapper.mapAccountChange(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body("{\"code\":\"INVALID\",\"message\":\"bad\"}"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError().getCode()).isEqualTo("INVALID");
    }

    @Test
    void rejectsMissingOrJsonNullRequiredBodies() {
        assertThatThrownBy(() -> mapper.mapAccountChange(ResponseEntity.ok().build()))
                .isInstanceOf(TechServicesClientException.class)
                .hasMessageContaining("empty response body");
        assertThatThrownBy(() -> mapper.mapAccountChange(ResponseEntity.ok("null")))
                .isInstanceOf(TechServicesClientException.class)
                .hasMessageContaining("null response body");
    }

    @Test
    void wrapsMalformedJsonAsClientExceptionWithCause() {
        assertThatThrownBy(() -> mapper.mapAccountChange(ResponseEntity.ok("{")))
                .isInstanceOf(TechServicesResponseMappingException.class)
                .hasMessageContaining("parse Tech Services response")
                .hasCauseInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
    }

    @Test
    void updateDetailsUsesUnexpectedResponseUnless200HasBody() {
        var response = mapper.mapUserDetails(ResponseEntity.status(HttpStatus.ACCEPTED)
                .body("{\"success\":true}"));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getError().getCode()).isEqualTo("UNEXPECTED_RESPONSE");
    }

    @Test
    void updateDetailsEmptyHttpErrorUsesStatusTextFallback() {
        var response = mapper.mapUserDetails(ResponseEntity.status(HttpStatus.NOT_FOUND).body(""));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getError().getCode()).isEqualTo("404 NOT_FOUND");
        assertThat(response.getError().getMessage()).isEqualTo("Not Found");
    }

    @Test
    void updateDetailsParsesNonemptyHttpError() {
        var response = mapper.mapUserDetails(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body("{\"code\":\"BAD_REQUEST\",\"message\":\"invalid\"}"));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getError().getMessage()).isEqualTo("invalid");
    }

    @Test
    void securityGroupSuccessRequiresNonNullBody() {
        assertThatThrownBy(() -> mapper.mapSecurityGroups(ResponseEntity.ok().build()))
                .isInstanceOf(TechServicesClientException.class);
    }
}

package uk.gov.justice.laa.datauserapi.application.command.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CcmsMessageTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void serialisesWithExpectedPropertyNames() throws Exception {
        CcmsMessage message = CcmsMessage.builder().userName("u").vendorNumber("V1").firstName("f").lastName("l")
                .timestamp(LocalDateTime.of(2026, 1, 2, 3, 4, 5)).email("e@x.com")
                .responsibilityKey(List.of("R1")).build();

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(message));

        assertThat(json.get("userName").asText()).isEqualTo("u");
        assertThat(json.get("vendorNumber").asText()).isEqualTo("V1");
        assertThat(json.get("timestampNow").asText()).isEqualTo("2026-01-02T03:04:05.000Z");
        assertThat(json.get("emailAddress").asText()).isEqualTo("e@x.com");
        assertThat(json.get("responsibilityKey").get(0).asText()).isEqualTo("R1");
    }

    @Test
    void omitsNullVendorNumber() throws Exception {
        CcmsMessage message = CcmsMessage.builder().userName("u").build();

        assertThat(objectMapper.readTree(objectMapper.writeValueAsString(message)).has("vendorNumber")).isFalse();
    }
}

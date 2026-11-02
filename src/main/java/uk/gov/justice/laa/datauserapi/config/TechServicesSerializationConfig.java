package uk.gov.justice.laa.datauserapi.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.justice.laa.datauserapi.client.ts.TechServicesResultMapper;

@Configuration
public class TechServicesSerializationConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Bean
    public TechServicesResultMapper techServicesResultMapper(ObjectMapper objectMapper) {
        return new TechServicesResultMapper(objectMapper);
    }
}

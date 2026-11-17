package uk.gov.justice.laa.datauserapi.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.justice.laa.datauserapi.client.ts.TechServicesResultMapper;

@Configuration
public class TechServicesSerializationConfig {

    @Bean
    public TechServicesResultMapper techServicesResultMapper(ObjectMapper objectMapper) {
        return new TechServicesResultMapper(objectMapper);
    }
}

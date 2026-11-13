package uk.gov.justice.laa.datauserapi.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.justice.laa.datauserapi.client.ts.AccessTokenProvider;
import uk.gov.justice.laa.datauserapi.client.ts.DoNothingTechServicesClient;
import uk.gov.justice.laa.datauserapi.client.ts.LiveTechServicesClient;
import uk.gov.justice.laa.datauserapi.client.ts.TechServicesApi;
import uk.gov.justice.laa.datauserapi.client.ts.TechServicesClient;
import uk.gov.justice.laa.datauserapi.client.ts.TechServicesResultMapper;
import uk.gov.justice.laa.datauserapi.client.ts.EntraOidValidator;

@Configuration
@EnableConfigurationProperties(TechServicesClientProperties.class)
public class TechServicesClientConfig {

    @Bean
    public EntraOidValidator techServicesEntraOidValidator() {
        return new EntraOidValidator();
    }

    @Bean
    @ConditionalOnProperty(value = "app.enable.tech.services.call", havingValue = "true", matchIfMissing = true)
    public TechServicesClient liveTechServicesClient(AccessTokenProvider tokenProvider, TechServicesApi api,
                                                    TechServicesResultMapper mapper, EntraOidValidator validator,
                                                    TechServicesClientProperties properties) {
        return new LiveTechServicesClient(tokenProvider, api, mapper, validator, properties.unit());
    }

    @Bean
    @ConditionalOnProperty(value = "app.enable.tech.services.call", havingValue = "false")
    public TechServicesClient doNothingTechServicesClient() {
        return new DoNothingTechServicesClient();
    }
}
